package com.example.nutriuniv.domain.logging.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그 저장을 <b>별도 트랜잭션</b>에서 돌린다.
 *
 * <p>예전에는 로그 저장을 요청 트랜잭션 안에서 try/catch 로 감쌌다. 그런데 repository.save 안에서 난 예외는
 * 잡아도 트랜잭션이 이미 rollback-only 로 표시돼 커밋 때 UnexpectedRollbackException(500)이 났다
 * (예: 컬럼보다 긴 값, 같은 세션의 동시 첫 요청, 읽기 전용 트랜잭션 안에서 부른 로그).
 * 여기서는 REQUIRES_NEW 로 따로 커밋하고, 실패하면 그 트랜잭션만 되돌린 뒤 예외를 삼킨다 — 부른 쪽 요청은 영향이 없다.
 */
@Slf4j
@Component
public class LogWriter {

    private final TransactionTemplate tx;

    public LogWriter(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 실패해도 예외를 던지지 않는다. what 은 실패 로그에 남길 이름. */
    public void write(String what, Runnable work) {
        try {
            tx.executeWithoutResult(status -> work.run());
        } catch (Exception e) {
            log.error("[{}] 로그 저장 실패 — {}", what, e.getMessage());
        }
    }
}
