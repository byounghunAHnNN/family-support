package com.familysupport.app.benefit.interfaces;

import com.familysupport.app.benefit.application.query.BenefitQueryService;
import com.familysupport.app.common.interfaces.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지원사업 조회 API.
 * 검색은 조회지만 소득·가구 상황을 URL에 남기지 않으려고 POST 본문으로 받는다.
 */
@RestController
@RequestMapping("/api/benefits")
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitQueryService benefitQueryService;
    private final BenefitMapper benefitMapper;

    @PostMapping("/search")
    public ApiResponse<BenefitSearchResponse> search(@RequestBody BenefitSearchRequest request) {
        return ApiResponse.ok(
                benefitMapper.toResponse(
                        benefitQueryService.search(benefitMapper.toProfile(request), request.keyword())));
    }

    @GetMapping("/regions")
    public ApiResponse<RegionsResponse> regions() {
        return ApiResponse.ok(
                benefitMapper.toRegionsResponse(
                        benefitQueryService.regions()));
    }
}
