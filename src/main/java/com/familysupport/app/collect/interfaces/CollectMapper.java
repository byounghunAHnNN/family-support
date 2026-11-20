package com.familysupport.app.collect.interfaces;

import com.familysupport.app.collect.domain.CollectRun;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** collect 영역 변환. */
@Component
public class CollectMapper {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CollectRunResponse toResponse(Optional<CollectRun> run) {
        return run.map(r -> new CollectRunResponse(
                        r.getStatus().name(),
                        format(r.getStartedAt()),
                        format(r.getFinishedAt()),
                        r.getSavedCount(),
                        r.getRemovedCount(),
                        r.getMessage()))
                .orElseGet(() -> new CollectRunResponse("NONE", null, null, 0, 0, null));
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : TIME.format(time);
    }
}
