package com.loopdfs.pos.service;

import com.loopdfs.pos.client.CountryInfoSoapClient;
import com.loopdfs.pos.dto.CountryDtos.*;
import com.loopdfs.pos.exception.CountryNotFoundException;
import com.loopdfs.pos.exception.SoapIntegrationException;
import com.loopdfs.pos.model.CountryInfo;
import com.loopdfs.pos.model.Language;
import com.loopdfs.pos.repository.CountryInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CountryInfoService {

    private final CountryInfoRepository countryInfoRepository;
    private final CountryInfoSoapClient soapClient;

    // ─── Fetch & persist from SOAP ────────────────────────────────────────────

    public CountryInfoResponse fetchAndSaveCountry(String rawName) {
        // Step 1: Convert to sentence case
        String countryName = toSentenceCase(rawName);
        log.info("Processing country lookup for: '{}'", countryName);

        // Step 2: Call SOAP to get ISO code
        String isoCode = soapClient.getCountryIsoCode(countryName);
        if (isoCode == null || isoCode.isBlank()) {
            throw new SoapIntegrationException(
                "No ISO code returned for country: " + countryName);
        }

        // If already persisted, return cached entry
        if (countryInfoRepository.existsByIsoCode(isoCode)) {
            log.info("Country '{}' already in DB, returning cached entry.", countryName);
            return toDto(countryInfoRepository.findByIsoCode(isoCode).orElseThrow());
        }

        // Step 3: Call SOAP to get full country info
        String fullInfoXml = soapClient.getFullCountryInfo(isoCode);

        // Step 4: Parse and persist
        CountryInfo entity = parseFullCountryInfo(fullInfoXml, isoCode);
        entity = countryInfoRepository.save(entity);
        log.info("Saved country info for '{}' with id={}", countryName, entity.getId());
        return toDto(entity);
    }

    // ─── CRUD operations ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CountryInfoResponse> getAllCountries() {
        return countryInfoRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CountryInfoResponse getCountryById(Long id) {
        CountryInfo entity = countryInfoRepository.findByIdWithLanguages(id)
                .orElseThrow(() -> new CountryNotFoundException(
                    "Country not found with id: " + id));
        return toDto(entity);
    }

    public CountryInfoResponse updateCountry(Long id, CountryUpdateRequest req) {
        CountryInfo entity = countryInfoRepository.findById(id)
                .orElseThrow(() -> new CountryNotFoundException(
                    "Country not found with id: " + id));

        if (req.getName()          != null) entity.setName(req.getName());
        if (req.getCapitalCity()   != null) entity.setCapitalCity(req.getCapitalCity());
        if (req.getPhoneCode()     != null) entity.setPhoneCode(req.getPhoneCode());
        if (req.getContinentCode() != null) entity.setContinentCode(req.getContinentCode());
        if (req.getCurrencyIsoCode() != null) entity.setCurrencyIsoCode(req.getCurrencyIsoCode());
        if (req.getCountryFlag()   != null) entity.setCountryFlag(req.getCountryFlag());

        entity = countryInfoRepository.save(entity);
        log.info("Updated country id={}", id);
        return toDto(entity);
    }

    public void deleteCountry(Long id) {
        if (!countryInfoRepository.existsById(id)) {
            throw new CountryNotFoundException("Country not found with id: " + id);
        }
        countryInfoRepository.deleteById(id);
        log.info("Deleted country id={}", id);
    }

    // ─── Parsing helper ───────────────────────────────────────────────────────

    private CountryInfo parseFullCountryInfo(String xml, String isoCode) {
        CountryInfo info = CountryInfo.builder()
                .name(           nvl(extractTag(xml, "sName"),          "Unknown"))
                .isoCode(        nvl(extractTag(xml, "sISOCode"),        isoCode))
                .capitalCity(    nvl(extractTag(xml, "sCapitalCity"),    ""))
                .phoneCode(      nvl(extractTag(xml, "sPhoneCode"),      ""))
                .continentCode(  nvl(extractTag(xml, "sContinentCode"),  ""))
                .currencyIsoCode(nvl(extractTag(xml, "sCurrencyISOCode"),""))
                .countryFlag(    nvl(extractTag(xml, "sCountryFlag"),    ""))
                .languages(      new ArrayList<>())
                .build();

        // Parse languages block
        Pattern langPattern = Pattern.compile(
            "<tLanguage>(.*?)</tLanguage>", Pattern.DOTALL);
        Matcher matcher = langPattern.matcher(xml);
        while (matcher.find()) {
            String block = matcher.group(1);
            Language lang = Language.builder()
                    .isoCode(nvl(extractTag(block, "sISOCode"), ""))
                    .name(   nvl(extractTag(block, "sName"),    "Unknown"))
                    .build();
            info.addLanguage(lang);
        }

        return info;
    }

    private String extractTag(String xml, String tag) {
        return soapClient.extractTagValue(xml, tag);
    }

    // ─── DTO mapper ───────────────────────────────────────────────────────────

    private CountryInfoResponse toDto(CountryInfo e) {
        List<LanguageDto> langs = e.getLanguages() == null ? List.of() :
            e.getLanguages().stream()
                .map(l -> LanguageDto.builder()
                        .id(l.getId())
                        .isoCode(l.getIsoCode())
                        .name(l.getName())
                        .build())
                .collect(Collectors.toList());

        return CountryInfoResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .isoCode(e.getIsoCode())
                .capitalCity(e.getCapitalCity())
                .phoneCode(e.getPhoneCode())
                .continentCode(e.getContinentCode())
                .currencyIsoCode(e.getCurrencyIsoCode())
                .countryFlag(e.getCountryFlag())
                .languages(langs)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /**
     * Converts a string to sentence case (first char upper, rest lower).
     * e.g. "kenya" → "Kenya", "KENYA" → "Kenya", "new zealand" → "New Zealand"
     */
    public String toSentenceCase(String input) {
        if (input == null || input.isBlank()) return input;
        String[] words = input.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
        }
        return sb.toString();
    }

    private String nvl(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val;
    }
}
