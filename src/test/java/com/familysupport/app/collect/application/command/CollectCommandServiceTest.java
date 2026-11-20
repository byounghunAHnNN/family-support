package com.familysupport.app.collect.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.BenefitRepository;
import com.familysupport.app.benefit.domain.BenefitSource;
import com.familysupport.app.collect.domain.BenefitFeed;
import com.familysupport.app.collect.domain.BenefitRaw;
import com.familysupport.app.collect.domain.BenefitRawRepository;
import com.familysupport.app.collect.domain.CollectRun;
import com.familysupport.app.collect.domain.CollectRunRepository;
import com.familysupport.app.collect.domain.CollectStatus;
import com.familysupport.app.collect.domain.FeedException;
import com.familysupport.app.support.Drafts;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

/** 수집 저장 규칙 — 실제 API 대신 가짜 출처를 넣고, 저장은 실제 DB(로컬 MySQL family_test)로 확인한다. */
@SpringBootTest
class CollectCommandServiceTest {

    @Autowired
    BenefitRepository benefitRepository;
    @Autowired
    CollectRunRepository collectRunRepository;
    @Autowired
    BenefitRawRepository benefitRawRepository;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    Clock clock;

    /** 돌려줄 목록을 바꿔 끼울 수 있는 가짜 출처. failing이면 FeedException. */
    static final class FakeFeed implements BenefitFeed {
        private final BenefitSource source;
        List<BenefitDraft> drafts = new ArrayList<>();
        boolean failing;
        Function<String, Optional<Benefit>> lastPrevious;

        FakeFeed(BenefitSource source) {
            this.source = source;
        }

        @Override
        public BenefitSource source() {
            return source;
        }

        @Override
        public List<BenefitDraft> fetch(Function<String, Optional<Benefit>> previous) {
            lastPrevious = previous;
            if (failing) {
                throw new FeedException("테스트 실패");
            }
            return drafts;
        }
    }

    @AfterEach
    void tearDown() {
        benefitRepository.deleteAll(benefitRepository.findAll());
    }

    @Test
    void 원문은_최신본_하나만_두고_바뀐_때만_변경시각을_갱신한다() {
        FakeFeed gov24 = new FakeFeed(BenefitSource.GOV24);
        CollectCommandService service = service(gov24);

        gov24.drafts = List.of(Drafts.gov24("1", "첫만남이용권").raw("{\"a\":1}", "{\"JA0410\":\"Y\"}").build());
        service.collect();
        BenefitRaw first = benefitRawRepository.findAll().get(0);

        // 수정일이 같아 조건을 다시 부르지 않은 경우(conditionBody null) — 지난 조건 원문을 유지하고 해시도 그대로
        gov24.drafts = List.of(Drafts.gov24("1", "첫만남이용권").raw("{\"a\":1}", null).build());
        service.collect();
        BenefitRaw same = benefitRawRepository.findAll().get(0);
        assertThat(same.getBodyHash()).isEqualTo(first.getBodyHash());
        assertThat(same.getConditionBody()).isEqualTo("{\"JA0410\":\"Y\"}");
        assertThat(same.getChangedAt()).isEqualTo(first.getChangedAt());

        gov24.drafts = List.of(Drafts.gov24("1", "첫만남이용권").raw("{\"a\":2}", null).build());
        service.collect();
        assertThat(benefitRawRepository.findAll().get(0).getBodyHash()).isNotEqualTo(first.getBodyHash());

        // 사업이 사라지면 원문도 지운다
        gov24.drafts = List.of();
        service.collect();
        assertThat(benefitRawRepository.findAll()).isEmpty();
    }

    private CollectCommandService service(FakeFeed... feeds) {
        return new CollectCommandService(List.of(feeds), benefitRepository, collectRunRepository, benefitRawRepository,
                transactionManager, clock);
    }

    @Test
    void 새로_넣고_갱신하고_사라진_사업은_지운다() {
        FakeFeed gov24 = new FakeFeed(BenefitSource.GOV24);
        CollectCommandService service = service(gov24);

        gov24.drafts = List.of(Drafts.gov24("1", "첫만남이용권").build(), Drafts.gov24("2", "출산축하금").build());
        CollectRun first = service.collect();
        assertThat(first.getStatus()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(benefitRepository.count()).isEqualTo(2);

        gov24.drafts = List.of(Drafts.gov24("1", "첫만남이용권(개정)").build());
        CollectRun second = service.collect();

        assertThat(second.getSavedCount()).isEqualTo(1);
        assertThat(second.getRemovedCount()).isEqualTo(1);
        assertThat(benefitRepository.findAll()).extracting(Benefit::getName).containsExactly("첫만남이용권(개정)");
        // 다음 수집 때 이전 저장본을 넘겨준다(지원조건 재사용용)
        assertThat(gov24.lastPrevious.apply("GOV24:1")).isPresent();
    }

    @Test
    void 실패한_출처의_기존_사업은_그대로_둔다() {
        FakeFeed gov24 = new FakeFeed(BenefitSource.GOV24);
        FakeFeed local = new FakeFeed(BenefitSource.BOKJIRO_LOCAL);
        CollectCommandService service = service(gov24, local);
        gov24.drafts = List.of(Drafts.gov24("1", "첫만남이용권").build());
        local.drafts = List.of(Drafts.bokjiroLocal("9", "출산축하금 지급").sigungu("서울특별시", "송파구").build());
        service.collect();

        local.failing = true;
        gov24.drafts = List.of();
        CollectRun run = service.collect();

        assertThat(run.getStatus()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(run.getMessage()).contains("BOKJIRO_LOCAL: 테스트 실패");
        // 정부24는 성공(0건)이라 지워지고, 실패한 복지로 지자체 사업은 남는다
        assertThat(benefitRepository.findAll()).extracting(Benefit::getId).containsExactly("BOKJIRO_LOCAL:9");
    }

    @Test
    void 모든_출처가_실패하면_FAILED() {
        FakeFeed gov24 = new FakeFeed(BenefitSource.GOV24);
        gov24.failing = true;
        assertThat(service(gov24).collect().getStatus()).isEqualTo(CollectStatus.FAILED);
    }
}
