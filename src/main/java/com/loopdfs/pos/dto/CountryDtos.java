package com.loopdfs.pos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

// ─── Inbound request ────────────────────────────────────────────────
public class CountryDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryRequest {
        @NotBlank(message = "Country name must not be blank")
        private String name;
    }

    // ─── Country info response ───────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CountryInfoResponse {
        private Long id;
        private String name;
        private String isoCode;
        private String capitalCity;
        private String phoneCode;
        private String continentCode;
        private String currencyIsoCode;
        private String countryFlag;
        private List<LanguageDto> languages;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ─── Language DTO ────────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LanguageDto {
        private Long id;
        private String isoCode;
        private String name;
    }

    // ─── Update request ──────────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryUpdateRequest {
        private String name;
        private String capitalCity;
        private String phoneCode;
        private String continentCode;
        private String currencyIsoCode;
        private String countryFlag;
    }

    // ─── Generic API wrapper ─────────────────────────────────────────
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private Object errors;

        public static <T> ApiResponse<T> ok(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> fail(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
