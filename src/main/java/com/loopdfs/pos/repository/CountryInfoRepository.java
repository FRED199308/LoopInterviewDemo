package com.loopdfs.pos.repository;

import com.loopdfs.pos.model.CountryInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CountryInfoRepository extends JpaRepository<CountryInfo, Long> {

    Optional<CountryInfo> findByIsoCode(String isoCode);

    Optional<CountryInfo> findByNameIgnoreCase(String name);

    boolean existsByIsoCode(String isoCode);

    @Query("SELECT c FROM CountryInfo c LEFT JOIN FETCH c.languages WHERE c.id = :id")
    Optional<CountryInfo> findByIdWithLanguages(@Param("id") Long id);
}
