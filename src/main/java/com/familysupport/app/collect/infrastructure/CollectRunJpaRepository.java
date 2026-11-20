package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.collect.domain.CollectRun;
import com.familysupport.app.collect.domain.CollectRunRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectRunJpaRepository extends JpaRepository<CollectRun, Long>, CollectRunRepository {
}
