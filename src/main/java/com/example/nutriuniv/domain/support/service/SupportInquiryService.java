package com.example.nutriuniv.domain.support.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.support.dto.InquiryCreateRequest;
import com.example.nutriuniv.domain.support.dto.InquiryCreateResponse;
import com.example.nutriuniv.domain.support.entity.SupportInquiry;
import com.example.nutriuniv.domain.support.repository.SupportInquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

/** 문의 접수 — content 빈 값·1000자 초과 400, 이메일 형식 400, 같은 사용자 하루 3건 초과 429 (KST, 소유자 → 세션 순). */
@Service
@RequiredArgsConstructor
public class SupportInquiryService {

    public static final int DAILY_LIMIT = 3;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private final SupportInquiryRepository repository;
    private final OwnerResolver ownerResolver;

    @Transactional
    public InquiryCreateResponse submit(InquiryCreateRequest request, Actor actor) {
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "content는 필수입니다.");
        }
        if (content.length() > SupportInquiry.MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "content는 " + SupportInquiry.MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
        }
        String category = request.getCategory() == null || request.getCategory().isBlank() ? null : request.getCategory().trim();
        if (category != null && category.length() > 20) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "category는 20자 이하여야 합니다.");
        }
        String email = request.getContactEmail() == null || request.getContactEmail().isBlank() ? null : request.getContactEmail().trim();
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "contactEmail 형식이 올바르지 않습니다.");
        }

        Owner owner = ownerResolver.resolveOrNull(actor);
        enforceDailyLimit(owner, actor.sessionId());

        SupportInquiry saved = repository.save(SupportInquiry.create(owner, actor.sessionId(), category, content, email));
        return new InquiryCreateResponse(saved.getId());
    }

    private void enforceDailyLimit(Owner owner, String sessionId) {
        ZonedDateTime start = LocalDate.now(KST).atStartOfDay(KST);
        ZoneId sys = ZoneId.systemDefault();
        LocalDateTime from = start.withZoneSameInstant(sys).toLocalDateTime();
        LocalDateTime to = start.plusDays(1).withZoneSameInstant(sys).toLocalDateTime();

        long count;
        if (owner != null && owner.isUser()) {
            count = repository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(owner.userId(), from, to);
        } else if (owner != null) {
            count = repository.countByAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(owner.anonymousId(), from, to);
        } else if (sessionId != null && !sessionId.isBlank()) {
            count = repository.countBySessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(sessionId, from, to);
        } else {
            return;
        }
        if (count >= DAILY_LIMIT) {
            throw new CustomException(ErrorCode.DAILY_LIMIT_EXCEEDED, "문의는 하루 " + DAILY_LIMIT + "건까지 접수할 수 있어요.");
        }
    }
}
