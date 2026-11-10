package com.example.nutriuniv.domain.admin.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.admin.dto.DeviceDeletionResponse;
import com.example.nutriuniv.domain.admin.service.DeviceRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Device", description = "기기 기록 삭제 (v3 ADM_006)")
@RestController
@RequiredArgsConstructor
public class AdminDeviceController {

    private final DeviceRecordService deviceRecordService;

    // DELETE /admin/devices/{anonymousId}/records
    @Operation(summary = "기기 기록 삭제",
            description = "사용자가 기기 ID(설정 > 기기 ID 복사)를 알려 주며 삭제를 요청했을 때. 되돌릴 수 없습니다. " +
                    "최근 본 제품 · 즐겨찾기 · 기기 목표 · 기기 등록을 지우고, 분석 요청 · 제보 · 이용 로그 · 이벤트는 기기 연결만 끊습니다. " +
                    "누가 언제 지웠는지 기기 ID 해시로 남깁니다. 표마다 지운 행 수를 돌려줍니다(없는 기기면 모두 0).")
    @DeleteMapping("/admin/devices/{anonymousId}/records")
    public ResponseEntity<CommonResponse<DeviceDeletionResponse>> delete(
            Actor actor,
            @Parameter(description = "기기 ID (X-Anonymous-Id 값)") @PathVariable String anonymousId,
            @Parameter(description = "삭제 사유 (선택, 200자)") @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(CommonResponse.success(deviceRecordService.delete(anonymousId, reason, actor.userId())));
    }
}
