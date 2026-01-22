package com.lh.assist.regulation.domain.enums.domain.repository;

import com.lh.assist.regulation.domain.enums.domain.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
	List<Document> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId);
}