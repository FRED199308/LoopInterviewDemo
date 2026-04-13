package com.loopdfs.pos.repository;

import com.loopdfs.pos.model.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LanguageRepository extends JpaRepository<Language, Long> {
    List<Language> findByCountryInfoId(Long countryId);
}
