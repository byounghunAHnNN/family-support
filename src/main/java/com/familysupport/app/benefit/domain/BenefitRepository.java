package com.familysupport.app.benefit.domain;

import java.util.List;

/** 지원사업 저장소 — JpaRepository 기본 메서드만 선언한다(거르기는 {@link Benefits}에서). */
public interface BenefitRepository {

    List<Benefit> findAll();

    <S extends Benefit> S save(S benefit);

    <S extends Benefit> List<S> saveAll(Iterable<S> benefits);

    void deleteAll(Iterable<? extends Benefit> benefits);

    long count();
}
