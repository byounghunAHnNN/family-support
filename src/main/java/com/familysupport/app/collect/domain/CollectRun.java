package com.familysupport.app.collect.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집 실행 1회 기록 — Aggregate 루트(하위 엔티티 없음). 마지막 수집 시각·결과를 보여주는 데 쓴다. */
@Entity
@Table(name = "collect_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectRun {

    private static final int MESSAGE_MAX = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    private CollectStatus status;

    private int savedCount;
    private int removedCount;
    private String message;

    public static CollectRun start(LocalDateTime now) {
        CollectRun run = new CollectRun();
        run.startedAt = now;
        run.status = CollectStatus.RUNNING;
        return run;
    }

    /**
     * 끝 표시. 실패한 출처가 없으면 SUCCESS, 일부만 실패하면 PARTIAL, 전부 실패하면 FAILED.
     *
     * @param failures   실패한 출처별 사유
     * @param feedCount  전체 출처 수
     */
    /** 저장 단계에서 예외가 나 아무것도 반영하지 못했을 때(저장 트랜잭션은 롤백됨). */
    public void fail(LocalDateTime now, String reason) {
        this.finishedAt = now;
        this.status = CollectStatus.FAILED;
        String text = "저장 실패: " + reason;
        this.message = text.length() > MESSAGE_MAX ? text.substring(0, MESSAGE_MAX) : text;
    }

    public void finish(LocalDateTime now, int saved, int removed, List<String> failures, int feedCount, String summary) {
        this.finishedAt = now;
        this.savedCount = saved;
        this.removedCount = removed;
        if (failures.isEmpty()) {
            this.status = CollectStatus.SUCCESS;
        } else {
            this.status = failures.size() >= feedCount ? CollectStatus.FAILED : CollectStatus.PARTIAL;
        }
        String text = summary + (failures.isEmpty() ? "" : " / 실패: " + String.join(" | ", failures));
        this.message = text.length() > MESSAGE_MAX ? text.substring(0, MESSAGE_MAX) : text;
    }
}
