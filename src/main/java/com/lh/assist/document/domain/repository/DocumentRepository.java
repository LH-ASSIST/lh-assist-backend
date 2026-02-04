package com.lh.assist.document.domain.repository;

import com.lh.assist.document.domain.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

	@Query("""
			select distinct d from Document d
			left join DocumentApproval a on a.document = d
			where d.user.userId = :userId or a.approverId = :userId
			order by d.createdAt desc
			""")
	List<Document> findAllAccessibleByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}