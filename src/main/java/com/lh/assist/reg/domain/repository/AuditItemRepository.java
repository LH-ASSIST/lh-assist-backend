package com.lh.assist.reg.domain.repository;

import com.lh.assist.reg.domain.entity.AuditItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditItemRepository extends JpaRepository<AuditItem, Long> {
}

