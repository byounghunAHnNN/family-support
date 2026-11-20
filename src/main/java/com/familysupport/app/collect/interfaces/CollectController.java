package com.familysupport.app.collect.interfaces;

import com.familysupport.app.collect.application.command.CollectCommandService;
import com.familysupport.app.common.interfaces.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수동 수집(백그라운드 시작). 인증이 없는 서비스라 운영에서는 끈다 —
 * family.collect.manual-enabled=true(local 프로필)일 때만 이 컨트롤러가 생긴다.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "family.collect.manual-enabled", havingValue = "true")
public class CollectController {

    private final CollectCommandService collectCommandService;

    /** @return 시작했으면 "STARTED". 이미 수집 중이면 409. */
    @PostMapping("/api/collect")
    public ApiResponse<String> start() {
        collectCommandService.startInBackground();
        return ApiResponse.ok("STARTED");
    }
}
