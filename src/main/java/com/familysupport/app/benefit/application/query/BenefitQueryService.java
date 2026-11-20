package com.familysupport.app.benefit.application.query;

import com.familysupport.app.benefit.domain.BenefitRepository;
import com.familysupport.app.benefit.domain.BenefitSearchResult;
import com.familysupport.app.benefit.domain.Benefits;
import com.familysupport.app.benefit.domain.HouseholdProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.SortedSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지원사업 조회. 저장된 사업을 전부 읽어 도메인 컬렉션({@link Benefits})에서 거른다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenefitQueryService {

    private final BenefitRepository benefitRepository;
    private final Clock clock;

    /** 내 상황·검색어로 사업을 찾는다. 확실히 해당하지 않는 사업은 빠진다. */
    public BenefitSearchResult search(HouseholdProfile profile, String keyword) {
        return new Benefits(benefitRepository.findAll()).search(profile, keyword, LocalDate.now(clock));
    }

    /** 화면 선택 목록용 시·도 → 시·군·구. */
    public Map<String, SortedSet<String>> regions() {
        return new Benefits(benefitRepository.findAll()).regions();
    }
}
