package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.entity.ProductSyncInboxFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ProductSyncInboxFileRepository extends JpaRepository<ProductSyncInboxFile, Long> {

    boolean existsByFileNameAndStatus(String fileName, ProductSyncInboxFile.Status status);

    /** 파일 여러 개의 처리 이력, 파일마다 최신 줄이 먼저. */
    List<ProductSyncInboxFile> findByFileNameInOrderByIdDesc(Collection<String> fileNames);

    /** 서버가 처리 도중 꺼져 PROCESSING 으로 남은 줄을 실패로 돌린다(부팅 때). */
    @Modifying
    @Transactional
    @Query("UPDATE ProductSyncInboxFile f SET f.status = com.example.nutriuniv.domain.publicdata.entity.ProductSyncInboxFile.Status.FAILED, " +
            "f.finishedAt = :now, f.failReason = :reason " +
            "WHERE f.status = com.example.nutriuniv.domain.publicdata.entity.ProductSyncInboxFile.Status.PROCESSING")
    int failInterrupted(@Param("now") LocalDateTime now, @Param("reason") String reason);
}
