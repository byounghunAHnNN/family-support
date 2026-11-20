package com.familysupport.app.benefit.domain;

/** 원문 날짜로 본 사업의 새로움 — 카드의 NEW·변경 배지. */
public enum Freshness {
    /** 원문 등록일이 최근 30일 안. */
    NEW,
    /** 원문 수정일이 최근 30일 안(새 등록은 아님). */
    UPDATED,
    NONE
}
