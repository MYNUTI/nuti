package com.example.nutriuniv.domain.logging.entity;

import com.example.nutriuniv.domain.logging.dto.LogContext;
import com.example.nutriuniv.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_view_logs",
        indexes = @Index(name = "idx_view_logs_anon", columnList = "anonymous_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "user_id")
    private Long userId;

    // 비로그인 식별자 (localStorage UUID) — 재방문 추적의 핵심
    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    // warm / cold
    @Column(name = "cohort", length = 10)
    private String cohort;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProductViewLog create(Product product, LogContext ctx) {
        ProductViewLog log = new ProductViewLog();
        log.product = product;
        log.userId = ctx.userId();
        log.anonymousId = ctx.anonymousId();
        log.sessionId = ctx.sessionId();
        log.cohort = ctx.cohort();
        return log;
    }
}
