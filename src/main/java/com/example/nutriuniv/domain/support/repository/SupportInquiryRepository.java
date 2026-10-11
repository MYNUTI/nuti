package com.example.nutriuniv.domain.support.repository;

import com.example.nutriuniv.domain.support.entity.SupportInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {

    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, LocalDateTime from, LocalDateTime to);

    long countByAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String anonymousId, LocalDateTime from, LocalDateTime to);

    long countBySessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String sessionId, LocalDateTime from, LocalDateTime to);
}
