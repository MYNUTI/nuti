package com.example.nutriuniv.domain.grade.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 등급 전량 재계산·기준 재산출은 한 번에 하나만 (기능명세서 10.3 「진행 중이면 막는다」 → 409).
 * 단일 인스턴스 전제의 프로세스 내 락. 인스턴스를 늘리면 DB 락(advisory lock)으로 바꿔야 한다.
 */
@Component
public class GradeEngineLock {

    private final ReentrantLock lock = new ReentrantLock();

    public boolean tryAcquire() {
        return lock.tryLock();
    }

    public void release() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public boolean isBusy() {
        return lock.isLocked();
    }
}
