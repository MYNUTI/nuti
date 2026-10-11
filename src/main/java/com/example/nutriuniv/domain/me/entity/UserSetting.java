package com.example.nutriuniv.domain.me.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 설정 (API 명세 /me/settings) — 소유자(user_id XOR anonymous_id)당 1행. 없으면 기본값(clipboardLinkDetection=true).
 * 로그인 병합(7.2) 시 계정에 설정이 없으면 익명 설정을 이관한다.
 */
@Entity
@Table(name = "user_settings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_settings_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_user_settings_anon", columnNames = "anonymous_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "clipboard_link_detection", nullable = false)
    private boolean clipboardLinkDetection = true;   // OFF 면 클라가 /links/resolve 를 호출하지 않는다

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static UserSetting create(Owner owner) {
        UserSetting s = new UserSetting();
        s.userId = owner.userId();
        s.anonymousId = owner.anonymousId();
        s.clipboardLinkDetection = true;
        s.updatedAt = LocalDateTime.now();
        return s;
    }

    public void changeClipboardLinkDetection(boolean on) {
        this.clipboardLinkDetection = on;
        this.updatedAt = LocalDateTime.now();
    }

    /** 병합(7.2): 익명 설정을 계정으로 (계정에 설정이 없을 때만 호출). */
    public void transferTo(Long userId) {
        this.userId = userId;
        this.anonymousId = null;
        this.updatedAt = LocalDateTime.now();
    }
}
