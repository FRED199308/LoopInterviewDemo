package com.loopdfs.pos.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "country_info", indexes = {
        @Index(name = "idx_iso_code", columnList = "iso_code"),
        @Index(name = "idx_country_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "iso_code", nullable = false, unique = true, length = 10)
    private String isoCode;

    @Column(name = "capital_city")
    private String capitalCity;

    @Column(name = "phone_code", length = 20)
    private String phoneCode;

    @Column(name = "continent_code", length = 10)
    private String continentCode;

    @Column(name = "currency_iso_code", length = 10)
    private String currencyIsoCode;

    @Column(name = "country_flag", length = 500)
    private String countryFlag;

    @OneToMany(mappedBy = "countryInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<Language> languages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper to maintain bidirectional relationship
    public void addLanguage(Language language) {
        languages.add(language);
        language.setCountryInfo(this);
    }
}
