package com.familysupport.app.benefit.infrastructure;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitJpaRepository extends JpaRepository<Benefit, String>, BenefitRepository {
}
