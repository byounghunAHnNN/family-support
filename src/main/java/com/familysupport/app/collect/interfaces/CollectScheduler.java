package com.familysupport.app.collect.interfaces;

import com.familysupport.app.collect.application.command.CollectCommandService;
import com.familysupport.app.common.error.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 수집 시작 시점 — 매일 정해진 시각, 그리고 서버 첫 시작(저장본이 비었을 때). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectScheduler {

    private final CollectCommandService collectCommandService;

    @Value("${family.collect.on-startup}")
    private boolean onStartup;

    @Scheduled(cron = "${family.collect.cron}", zone = "Asia/Seoul")
    public void daily() {
        try {
            collectCommandService.startInBackground();
        } catch (ConflictException e) {
            log.info("정기 수집 건너뜀: {}", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (onStartup) {
            collectCommandService.startInBackgroundIfEmpty();
        }
    }
}
