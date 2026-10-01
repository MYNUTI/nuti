package com.example.nutriuniv.domain.link.repository;

import com.example.nutriuniv.domain.link.entity.LinkResolve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LinkResolveRepository extends JpaRepository<LinkResolve, String> {

    // 같은 링크 24시간 캐시
    Optional<LinkResolve> findFirstByUrlHashAndExpiresAtAfterOrderByCreatedAtDesc(String urlHash, LocalDateTime now);
}
