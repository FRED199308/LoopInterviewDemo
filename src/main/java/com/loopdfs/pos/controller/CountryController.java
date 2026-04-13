package com.loopdfs.pos.controller;

import com.loopdfs.pos.dto.CountryDtos;
import com.loopdfs.pos.dto.CountryDtos.*;
import com.loopdfs.pos.service.CountryInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Countries", description = "Fetch country info via SOAP and manage stored records")
public class CountryController {

    private final CountryInfoService countryInfoService;

    @Operation(
            summary = "Fetch and save country information",
            description = "Accepts a country name, converts it to sentence case, calls SOAP CountryISOCode then FullCountryInfo, and persists the result. Returns cached record if already stored."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Country fetched and saved successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error - name is blank",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Validation failed\",\"errors\":{\"name\":\"Country name must not be blank\"}}"))
            ),
            @ApiResponse(responseCode = "502", description = "SOAP service unreachable or returned an error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"External service error: Unable to fetch ISO code after retries.\"}"))
            )
    })
    @PostMapping
    public ResponseEntity<CountryDtos.ApiResponse<CountryInfoResponse>> createCountry(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Country name to look up (case-insensitive). Example: {\"name\": \"kenya\"}",
                    required = true)
            @Valid @RequestBody CountryRequest request) {

        log.info("POST /api/v1/countries - name='{}'", request.getName());
        CountryInfoResponse response = countryInfoService.fetchAndSaveCountry(request.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CountryDtos.ApiResponse.ok(
                        "Country information fetched and saved successfully.", response));
    }

    @Operation(summary = "List all stored countries",
            description = "Returns all country records currently persisted in the database, including their languages.")
    @ApiResponse(responseCode = "200", description = "List returned successfully")
    @GetMapping
    public ResponseEntity<CountryDtos.ApiResponse<List<CountryInfoResponse>>> getAllCountries() {
        log.info("GET /api/v1/countries");
        List<CountryInfoResponse> countries = countryInfoService.getAllCountries();
        return ResponseEntity.ok(CountryDtos.ApiResponse.ok(
                "Retrieved " + countries.size() + " country records.", countries));
    }

    @Operation(summary = "Get a country by ID",
            description = "Fetches a single country record by its database ID, including all associated languages.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Country found"),
            @ApiResponse(responseCode = "404", description = "No country found with the given ID")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CountryDtos.ApiResponse<CountryInfoResponse>> getCountryById(
            @Parameter(description = "Database ID of the country", example = "1")
            @PathVariable Long id) {
        log.info("GET /api/v1/countries/{}", id);
        CountryInfoResponse response = countryInfoService.getCountryById(id);
        return ResponseEntity.ok(CountryDtos.ApiResponse.ok("Country found.", response));
    }

    @Operation(summary = "Update a country record",
            description = "Partially updates a stored country. Only fields included in the request body are changed; omitted fields are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Country updated successfully"),
            @ApiResponse(responseCode = "404", description = "Country not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<CountryDtos.ApiResponse<CountryInfoResponse>> updateCountry(
            @Parameter(description = "Database ID of the country to update", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Fields to update – all are optional. Example: {\"capitalCity\": \"Nairobi\", \"phoneCode\": \"+254\"}",
                    required = true)
            @RequestBody CountryUpdateRequest request) {
        log.info("PUT /api/v1/countries/{}", id);
        CountryInfoResponse response = countryInfoService.updateCountry(id, request);
        return ResponseEntity.ok(CountryDtos.ApiResponse.ok(
                "Country updated successfully.", response));
    }

    @Operation(summary = "Delete a country record",
            description = "Permanently deletes a country and all its associated language records from the database.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Country deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Country not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CountryDtos.ApiResponse<Void>> deleteCountry(
            @Parameter(description = "Database ID of the country to delete", example = "1")
            @PathVariable Long id) {
        log.info("DELETE /api/v1/countries/{}", id);
        countryInfoService.deleteCountry(id);
        return ResponseEntity.ok(CountryDtos.ApiResponse.ok(
                "Country deleted successfully.", null));
    }
}