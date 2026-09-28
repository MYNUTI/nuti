package com.example.nutriuniv.domain.analysis.repository;

import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestChannel;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AnalysisRequestEventRepository extends JpaRepository<AnalysisRequestEvent, Long> {

    long countByChannelAndUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            AnalysisRequestChannel channel, Long userId, LocalDateTime from, LocalDateTime to);

    long countByChannelAndAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            AnalysisRequestChannel channel, String anonymousId, LocalDateTime from, LocalDateTime to);

    long countByChannelAndSessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            AnalysisRequestChannel channel, String sessionId, LocalDateTime from, LocalDateTime to);
}
