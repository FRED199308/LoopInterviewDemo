package com.loopdfs.pos.client;

import com.loopdfs.pos.exception.SoapIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CountryInfoSoapClient {

    @Value("${soap.endpoint.url}")
    private String endpointUrl;

    private static final String SOAP_NS =
            "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "xmlns:web=\"http://www.oorsprong.org/websamples.countryinfo\"";

    // ─── 1. Get ISO code by country name ─────────────────────────────────────

    @Retryable(retryFor = SoapIntegrationException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public String getCountryIsoCode(String countryName) {
        log.info("Calling SOAP CountryISOCode for: {}", countryName);
        String body =
                "<soapenv:Envelope " + SOAP_NS + ">" +
                        "<soapenv:Header/>" +
                        "<soapenv:Body>" +
                        "<web:CountryISOCode>" +
                        "<web:sCountryName>" + escapeXml(countryName) + "</web:sCountryName>" +
                        "</web:CountryISOCode>" +
                        "</soapenv:Body>" +
                        "</soapenv:Envelope>";

        String response = post(body);
        String isoCode = extractTagValue(response, "CountryISOCodeResult");
        log.info("ISO code for '{}': {}", countryName, isoCode);
        return isoCode;
    }

    @Recover
    public String recoverGetIsoCode(SoapIntegrationException ex, String countryName) {
        throw new SoapIntegrationException(
                "Unable to fetch ISO code for '" + countryName + "' after retries.", ex);
    }

    // ─── 2. Get full country info by ISO code ─────────────────────────────────

    @Retryable(retryFor = SoapIntegrationException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public String getFullCountryInfo(String isoCode) {
        log.info("Calling SOAP FullCountryInfo for ISO: {}", isoCode);
        String body =
                "<soapenv:Envelope " + SOAP_NS + ">" +
                        "<soapenv:Header/>" +
                        "<soapenv:Body>" +
                        "<web:FullCountryInfo>" +
                        "<web:sCountryISOCode>" + escapeXml(isoCode) + "</web:sCountryISOCode>" +
                        "</web:FullCountryInfo>" +
                        "</soapenv:Body>" +
                        "</soapenv:Envelope>";

        String response = post(body);
        log.debug("FullCountryInfo response for ISO={}: {}", isoCode, response);
        return response;
    }

    @Recover
    public String recoverGetFullInfo(SoapIntegrationException ex, String isoCode) {
        throw new SoapIntegrationException(
                "Unable to fetch full info for '" + isoCode + "' after retries.", ex);
    }

    // ─── Raw HTTP POST ────────────────────────────────────────────────────────

    private String post(String soapBody) {
        try {
            // Confirm exactly what we are sending – this must NOT have <?xml...?>
            log.info("=== EXACT BYTES BEING SENT TO SERVER ===\n{}", soapBody);

            byte[] bytes = soapBody.getBytes(StandardCharsets.UTF_8);

            HttpURLConnection conn = (HttpURLConnection) new URL(endpointUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", "\"\"");
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            log.info("=== SERVER RESPONDED WITH HTTP {} ===", status);

            var stream = (status >= 200 && status < 300)
                    ? conn.getInputStream() : conn.getErrorStream();

            String response = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            if (status != 200) {
                log.error("=== SERVER ERROR BODY ===\n{}", response);
                throw new SoapIntegrationException(
                        "SOAP service returned HTTP " + status + ": " + response);
            }

            return response;

        } catch (SoapIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("SOAP call failed: {}", e.getMessage(), e);
            throw new SoapIntegrationException("SOAP call failed: " + e.getMessage(), e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public String extractTagValue(String xml, String tagName) {
        for (String prefix : new String[]{"", "m:", "web:", "tns:"}) {
            String open  = "<"  + prefix + tagName + ">";
            String close = "</" + prefix + tagName + ">";
            int start = xml.indexOf(open);
            if (start >= 0) {
                start += open.length();
                int end = xml.indexOf(close, start);
                if (end >= 0) return xml.substring(start, end).trim();
            }
        }
        log.warn("Tag '{}' not found in response", tagName);
        return null;
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}