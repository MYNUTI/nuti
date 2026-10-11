package com.example.nutriuniv.domain.link.repository;

import com.example.nutriuniv.domain.link.entity.LinkResolveFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LinkResolveFeedbackRepository extends JpaRepository<LinkResolveFeedback, Long> {

    // 같은 링크에 대해 사용자가 바로잡은 제품이 있으면 다음 인식 때 그것을 먼저 쓴다
    Optional<LinkResolveFeedback> findFirstByUrlHashAndCorrectedProductIdIsNotNullOrderByCreatedAtDesc(String urlHash);
}
