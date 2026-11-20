package com.familysupport.app.collect.domain;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.BenefitSource;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 사업 목록을 가져오는 곳(출처 1개). 구현은 infrastructure(공공 API 호출)에 있다.
 * 대상 지역·대상 가구 상황에 맞는 사업만 돌려준다.
 */
public interface BenefitFeed {

    BenefitSource source();

    /**
     * @param previous 이미 저장된 사업 찾기(ID → 사업). 원문이 바뀌지 않은 사업은 추가 호출을 줄이는 데 쓴다.
     * @throws FeedException 출처를 끝까지 읽지 못함 — 이 출처의 기존 저장본은 그대로 둔다
     */
    List<BenefitDraft> fetch(Function<String, Optional<Benefit>> previous);
}
