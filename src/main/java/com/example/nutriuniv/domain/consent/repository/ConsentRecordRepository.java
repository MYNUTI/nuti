package com.example.nutriuniv.domain.consent.repository;

import com.example.nutriuniv.domain.consent.entity.ConsentItemCode;
import com.example.nutriuniv.domain.consent.entity.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    Optional<ConsentRecord> findTopByUserIdAndItemCodeOrderByCreatedAtDescIdDesc(Long userId, ConsentItemCode itemCode);

    List<ConsentRecord> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    /** 계정 삭제 (ACCT_008) — 동의 이력도 지운다. */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ConsentRecord r WHERE r.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
