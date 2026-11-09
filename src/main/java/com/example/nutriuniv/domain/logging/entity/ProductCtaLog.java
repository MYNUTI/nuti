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
@Table(name = "product_cta_logs",
        indexes = {
                @Index(name = "idx_cta_logs_user_product", columnList = "user_id, product_id"),
                @Index(name = "idx_cta_logs_anon", columnList = "anonymous_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductCtaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "cohort", length = 10)
    private String cohort;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProductCtaLog create(Product product, LogContext ctx) {
        ProductCtaLog log = new ProductCtaLog();
        log.product = product;
        log.userId = ctx.userId();
        log.anonymousId = ctx.anonymousId();
        log.sessionId = ctx.sessionId();
        log.cohort = ctx.cohort();
        return log;
    }
}
