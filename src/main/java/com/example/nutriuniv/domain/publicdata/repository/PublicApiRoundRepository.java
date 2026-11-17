package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublicApiRoundRepository extends JpaRepository<PublicApiRound, Long> {

    /** API 하나에 RUNNING 은 하나만. */
    Optional<PublicApiRound> findFirstByApiAndStatus(String api, PublicApiRound.Status status);

    Optional<PublicApiRound> findTopByApiOrderByIdDesc(String api);

    Optional<PublicApiRound> findTopByApiAndStatusOrderByIdDesc(String api, PublicApiRound.Status status);

    Optional<PublicApiRound> findTopByApiAndModeOrderByIdDesc(String api, PublicApiRound.Mode mode);

    Optional<PublicApiRound> findTopByApiAndModeAndStatusOrderByIdDesc(String api, PublicApiRound.Mode mode, PublicApiRound.Status status);

    List<PublicApiRound> findByStatusOrderByIdAsc(PublicApiRound.Status status);

    /** 이 API 들 중 마지막으로 끝까지 받은 시각 — 재계산할 새 데이터가 있는지 본다. 없으면 null. */
    @Query("select max(r.finishedAt) from PublicApiRound r where r.status = :status and r.api in :apis")
    LocalDateTime maxFinishedAt(@Param("status") PublicApiRound.Status status, @Param("apis") Collection<String> apis);
}