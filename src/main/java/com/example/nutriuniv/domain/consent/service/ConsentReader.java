package com.example.nutriuniv.domain.consent.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.entity.ConsentItemCode;
import com.example.nutriuniv.domain.consent.entity.ConsentRecord;
import com.example.nutriuniv.domain.consent.repository.ConsentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * 동의 상태 읽기 — 목표 · 몸 정보 · 병합처럼 동의를 확인만 하는 곳이 쓴다(ConsentService 와의 순환을 피하려고 따로 둔다).
 * 기기 ID 소유자는 동의가 없으므로 늘 false.
 */
@Service
@RequiredArgsConstructor
public class ConsentReader {

    private final ConsentRecordRepository repository;

    @Transactional(readOnly = true)
    public boolean isAgreed(Long userId, ConsentItemCode item) {
        if (userId == null) return false;
        return repository.findTopByUserIdAndItemCodeOrderByCreatedAtDescIdDesc(userId, item)
                .map(ConsentRecord::isAgreed).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isAgreed(Owner owner, ConsentItemCode item) {
        return owner != null && owner.isUser() && isAgreed(owner.userId(), item);
    }

    /** 항목별 마지막 기록. 기록이 없는 항목은 키가 없다. */
    @Transactional(readOnly = true)
    public Map<ConsentItemCode, ConsentRecord> latest(Long userId) {
        Map<ConsentItemCode, ConsentRecord> out = new EnumMap<>(ConsentItemCode.class);
        if (userId == null) return out;
        for (ConsentRecord r : repository.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            out.put(r.getItemCode(), r);
        }
        return out;
    }
}
