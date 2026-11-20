package com.familysupport.app.collect.domain;

import java.util.List;
import java.util.Optional;

/** 수집 기록 저장소 — JpaRepository 기본 메서드만. */
public interface CollectRunRepository {

    List<CollectRun> findAll();

    Optional<CollectRun> findById(Long id);

    <S extends CollectRun> S save(S run);
}
