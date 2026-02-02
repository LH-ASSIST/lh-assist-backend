package com.lh.assist.approval.domain.repository;

import com.lh.assist.approval.domain.entity.DocumentApproval;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentApprovalRepository extends JpaRepository<DocumentApproval, Long> {
    Optional<DocumentApproval> findByDocument_DocId(Long docId);
}