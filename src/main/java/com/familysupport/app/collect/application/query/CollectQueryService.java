package com.familysupport.app.collect.application.query;

import com.familysupport.app.collect.domain.CollectRun;
import com.familysupport.app.collect.domain.CollectRunRepository;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집 기록 조회. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectQueryService {

    private final CollectRunRepository collectRunRepository;

    /** 가장 최근 실행(진행 중 포함). 기록이 하루 1건 정도라 findAll 후 Java에서 고른다. */
    public Optional<CollectRun> lastRun() {
        return collectRunRepository.findAll().stream().max(Comparator.comparing(CollectRun::getId));
    }
}
