package com.familysupport.app.collect.application.command;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitClassifier;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.BenefitRepository;
import com.familysupport.app.benefit.domain.BenefitSource;
import com.familysupport.app.collect.domain.BenefitFeed;
import com.familysupport.app.collect.domain.BenefitRaw;
import com.familysupport.app.collect.domain.BenefitRawRepository;
import com.familysupport.app.collect.domain.CollectRun;
import com.familysupport.app.collect.domain.CollectRunRepository;
import com.familysupport.app.common.error.ConflictException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 전체 수집 — 모든 출처를 읽어 지원사업 저장본을 새로 맞춘다.
 *
 * <p>⚠ 개발규칙 예외: 클래스에 {@code @Transactional}을 걸지 않는다. 외부 API 호출이 수 분 걸려 그동안 DB 트랜잭션을
 * 잡고 있지 않도록, 호출은 트랜잭션 밖에서 하고 저장만 {@link TransactionTemplate}으로 짧게 묶는다.
 *
 * <p>규칙
 * <ul>
 *   <li>동시에 한 번만 돈다(이미 돌고 있으면 409).</li>
 *   <li>실패한 출처의 기존 저장본은 그대로 둔다 — 잠깐의 API 장애로 사업이 화면에서 사라지지 않게.</li>
 *   <li>성공한 출처에서 이번에 안 나온 사업은 지운다(종료·대상 변경된 사업).</li>
 * </ul>
 */
@Slf4j
@Service
public class CollectCommandService {

    private final List<BenefitFeed> feeds;
    private final BenefitRepository benefitRepository;
    private final CollectRunRepository collectRunRepository;
    private final BenefitRawRepository benefitRawRepository;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CollectCommandService(List<BenefitFeed> feeds, BenefitRepository benefitRepository,
            CollectRunRepository collectRunRepository, BenefitRawRepository benefitRawRepository,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.feeds = feeds;
        this.benefitRepository = benefitRepository;
        this.collectRunRepository = collectRunRepository;
        this.benefitRawRepository = benefitRawRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 백그라운드로 수집을 시작한다(바로 돌아온다).
     * @throws ConflictException 이미 수집 중
     */
    public void startInBackground() {
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("이미 수집 중입니다. 끝난 뒤 다시 시도해 주세요.");
        }
        Thread thread = new Thread(() -> {
            try {
                collectHoldingLock();
            } catch (RuntimeException e) {
                log.error("수집 실패", e);
            } finally {
                running.set(false);
            }
        }, "benefit-collect");
        thread.setDaemon(true);
        thread.start();
    }

    /** 저장된 사업이 하나도 없을 때만 백그라운드 수집(서버 첫 시작용). 이미 수집 중이면 아무것도 하지 않는다. */
    public void startInBackgroundIfEmpty() {
        if (benefitRepository.count() == 0 && !running.get()) {
            startInBackground();
        }
    }

    /**
     * 지금 스레드에서 수집하고 기록을 돌려준다(스케줄·테스트용).
     * @throws ConflictException 이미 수집 중
     */
    public CollectRun collect() {
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("이미 수집 중입니다. 끝난 뒤 다시 시도해 주세요.");
        }
        try {
            return collectHoldingLock();
        } finally {
            running.set(false);
        }
    }

    private CollectRun collectHoldingLock() {
        Long runId = tx.execute(s -> collectRunRepository.save(CollectRun.start(now())).getId());
        Map<String, Benefit> previous = tx.execute(s -> benefitRepository.findAll().stream()
                .collect(Collectors.toMap(Benefit::getId, Function.identity())));
        Map<BenefitSource, List<BenefitDraft>> fetched = new EnumMap<>(BenefitSource.class);
        List<String> failures = new ArrayList<>();
        for (BenefitFeed feed : feeds) {
            try {
                fetched.put(feed.source(), feed.fetch(id -> Optional.ofNullable(previous.get(id))));
            } catch (RuntimeException e) {
                log.warn("{} 수집 실패: {}", feed.source(), e.getMessage());
                failures.add(feed.source().name() + ": " + e.getMessage());
            }
        }
        alignAcrossSources(fetched);
        try {
            return tx.execute(s -> saveAll(runId, fetched, failures));
        } catch (RuntimeException e) {
            // 저장이 롤백돼도 실행 기록은 RUNNING으로 남지 않게 따로 FAILED로 남긴다(기존 저장본은 그대로)
            tx.executeWithoutResult(s -> collectRunRepository.findById(runId).orElseThrow()
                    .fail(now(), e.getClass().getSimpleName() + " — " + rootMessage(e)));
            throw e;
        }
    }

    /** 출처 사이 맞추기 후 대상 상황이 없는 초안은 버린다(성공한 출처끼리만). */
    private static void alignAcrossSources(Map<BenefitSource, List<BenefitDraft>> fetched) {
        List<BenefitDraft> all = fetched.values().stream().flatMap(List::stream).toList();
        Map<BenefitSource, List<BenefitDraft>> aligned = BenefitClassifier.alignAcrossSources(all).stream()
                .collect(Collectors.groupingBy(BenefitDraft::source));
        fetched.replaceAll((source, list) -> aligned.getOrDefault(source, List.of()));
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = String.valueOf(root.getMessage());
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    /** 성공한 출처의 사업을 새로 넣거나 갱신하고, 이번에 안 나온 사업은 지운다. */
    private CollectRun saveAll(Long runId, Map<BenefitSource, List<BenefitDraft>> fetched, List<String> failures) {
        LocalDateTime now = now();
        Map<String, Benefit> existing = benefitRepository.findAll().stream()
                .collect(Collectors.toMap(Benefit::getId, Function.identity()));
        // 출처 안에서 같은 ID가 두 번 오면 마지막 것을 쓴다
        Map<String, BenefitDraft> drafts = new LinkedHashMap<>();
        fetched.values().forEach(list -> list.forEach(d -> drafts.put(d.id(), d)));

        List<Benefit> toSave = new ArrayList<>();
        for (BenefitDraft draft : drafts.values()) {
            Benefit current = existing.get(draft.id());
            if (current == null) {
                toSave.add(Benefit.create(draft, now));
            } else {
                current.refresh(draft, now);
            }
        }
        benefitRepository.saveAll(toSave);
        saveRaws(drafts.values(), now);

        List<Benefit> stale = existing.values().stream()
                .filter(b -> fetched.containsKey(b.getSource()) && !drafts.containsKey(b.getId()))
                .toList();
        // 원문은 FK(ON DELETE CASCADE)로도 지워지지만, 영속성 컨텍스트와 어긋나지 않게 먼저 직접 지운다
        benefitRawRepository.deleteAllById(stale.stream().map(Benefit::getId).toList());
        benefitRepository.deleteAll(stale);

        String summary = fetched.entrySet().stream()
                .map(e -> e.getKey().name() + " " + e.getValue().size() + "건")
                .collect(Collectors.joining(", "));
        CollectRun run = collectRunRepository.findById(runId).orElseThrow();
        run.finish(now, drafts.size(), stale.size(), failures, feeds.size(), summary);
        log.info("수집 끝: {} — 저장 {}건, 삭제 {}건, 실패 출처 {}개", summary, drafts.size(), stale.size(), failures.size());
        return run;
    }

    /** 출처 원문을 최신본 1개로 맞춘다(원문이 바뀐 사업만 changed_at 갱신). 원문이 없는 초안은 건너뛴다. */
    private void saveRaws(Collection<BenefitDraft> drafts, LocalDateTime now) {
        Map<String, BenefitRaw> existing = benefitRawRepository.findAll().stream()
                .collect(Collectors.toMap(BenefitRaw::getBenefitId, Function.identity()));
        List<BenefitRaw> toSave = new ArrayList<>();
        for (BenefitDraft draft : drafts) {
            if (draft.raw() == null) {
                continue;
            }
            BenefitRaw current = existing.get(draft.id());
            if (current == null) {
                toSave.add(BenefitRaw.create(draft.id(), draft.raw(), now));
            } else {
                current.refresh(draft.raw(), now);
            }
        }
        benefitRawRepository.saveAll(toSave);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
