package com.lh.assist.reg.domain.repository;

import com.lh.assist.reg.domain.entity.Regulation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulationRepository extends JpaRepository<Regulation, Long> {
}
