package com.lh.assist.reg.domain.repository;

import com.lh.assist.reg.domain.entity.RegItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegItemRepository extends JpaRepository<RegItem, Long> {
}

