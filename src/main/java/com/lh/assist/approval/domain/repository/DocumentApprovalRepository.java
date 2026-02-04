package com.lh.assist.approval.domain.repository;

import com.lh.assist.approval.domain.entity.DocumentApproval;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentApprovalRepository extends JpaRepository<DocumentApproval, Long> {
    Optional<DocumentApproval> findByDocument_DocId(Long docId);
    boolean existsByDocument_DocIdAndApproverId(Long docId, Long approverId);

    @Query("select a.document.docId from DocumentApproval a where a.approverId = :approverId")
    List<Long> findDocumentIdsByApproverId(@Param("approverId") Long approverId);
}