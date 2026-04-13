package com.loopdfs.pos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPConstants;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class SoapConfig {

    @Value("${soap.endpoint.url}")
    private String soapEndpointUrl;

    /**
     * SAAJ message factory configured for SOAP 1.1.
     * SAAJ gives us direct control over serialization — importantly,
     * it does NOT prepend the <?xml?> declaration when writing to the wire.
     */
    @Bean
    public SaajSoapMessageFactory messageFactory() throws Exception {
        SaajSoapMessageFactory factory = new SaajSoapMessageFactory(
                MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL));
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public HttpUrlConnectionMessageSender messageSender() {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofSeconds(10));
        sender.setReadTimeout(Duration.ofSeconds(30));
        return sender;
    }

    @Bean
    public WebServiceTemplate webServiceTemplate(
            SaajSoapMessageFactory messageFactory,
            HttpUrlConnectionMessageSender messageSender) {

        WebServiceTemplate template = new WebServiceTemplate(messageFactory);
        template.setDefaultUri(soapEndpointUrl);
        template.setMessageSender(messageSender);
        log.info("WebServiceTemplate configured with SAAJ SOAP 1.1 factory → {}", soapEndpointUrl);
        return template;
    }
}