package com.lh.assist.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
	List<Document> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId);
}