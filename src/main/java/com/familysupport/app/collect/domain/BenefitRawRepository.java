package com.familysupport.app.collect.domain;

import java.util.List;

/** 원문 보관 저장소 — JpaRepository 기본 메서드만. */
public interface BenefitRawRepository {

    List<BenefitRaw> findAll();

    <S extends BenefitRaw> List<S> saveAll(Iterable<S> raws);

    void deleteAllById(Iterable<? extends String> ids);
}
