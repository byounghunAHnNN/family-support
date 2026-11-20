package com.familysupport.app.collect.interfaces;

/** 최근 수집 결과. 기록이 없으면 status = "NONE". */
public record CollectRunResponse(
        String status,
        String startedAt,
        String finishedAt,
        int savedCount,
        int removedCount,
        String message) {
}
