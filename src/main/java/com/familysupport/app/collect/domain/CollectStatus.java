package com.familysupport.app.collect.domain;

/** 수집 실행 상태. PARTIAL = 일부 출처 실패(실패한 출처는 이전 저장본 유지). */
public enum CollectStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED
}
