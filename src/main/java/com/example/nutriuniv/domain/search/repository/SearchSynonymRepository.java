package com.example.nutriuniv.domain.search.repository;

import com.example.nutriuniv.domain.search.entity.SearchSynonym;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchSynonymRepository extends JpaRepository<SearchSynonym, Long> {

    List<SearchSynonym> findByTermAndIsActiveTrue(String term);

    List<SearchSynonym> findByCanonicalAndIsActiveTrue(String canonical);

    List<SearchSynonym> findByIsActiveTrue();
}
