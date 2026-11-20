package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.collect.domain.BenefitRaw;
import com.familysupport.app.collect.domain.BenefitRawRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitRawJpaRepository extends JpaRepository<BenefitRaw, String>, BenefitRawRepository {
}
