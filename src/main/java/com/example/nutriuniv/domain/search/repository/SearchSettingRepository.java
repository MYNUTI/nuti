package com.example.nutriuniv.domain.search.repository;

import com.example.nutriuniv.domain.search.entity.SearchSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchSettingRepository extends JpaRepository<SearchSetting, String> {
}
