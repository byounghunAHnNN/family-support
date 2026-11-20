package com.familysupport.app.collect.interfaces;

import com.familysupport.app.collect.application.query.CollectQueryService;
import com.familysupport.app.common.interfaces.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 최근 수집 결과 조회(화면의 "마지막 수집" 표시·진행 확인). */
@RestController
@RequiredArgsConstructor
public class CollectStatusController {

    private final CollectQueryService collectQueryService;
    private final CollectMapper collectMapper;

    @GetMapping("/api/collect/status")
    public ApiResponse<CollectRunResponse> status() {
        return ApiResponse.ok(
                collectMapper.toResponse(
                        collectQueryService.lastRun()));
    }
}
