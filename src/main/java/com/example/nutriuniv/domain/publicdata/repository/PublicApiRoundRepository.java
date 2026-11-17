package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicApiRoundRepository extends JpaRepository<PublicApiRound, Long> {

    /** API 하나에 RUNNING 은 하나만. */
    Optional<PublicApiRound> findFirstByApiAndStatus(String api, PublicApiRound.Status status);

    Optional<PublicApiRound> findTopByApiOrderByIdDesc(String api);

    Optional<PublicApiRound> findTopByApiAndStatusOrderByIdDesc(String api, PublicApiRound.Status status);

    Optional<PublicApiRound> findTopByApiAndModeAndStatusOrderByIdDesc(String api, PublicApiRound.Mode mode, PublicApiRound.Status status);
}