package com.lh.assist.suggestion.domain.repository;

import com.lh.assist.suggestion.domain.entity.Suggestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // 모든 건의게시글을 조회한다
    @Override
    @EntityGraph(attributePaths = "user")
    @NonNull
    Page<Suggestion> findAll(@NonNull Pageable pageable);

    // 조회수를 지정한 값(delta)만큼 직접 증가시킨다
    @Modifying
    @Query("update Suggestion s set s.viewCount = s.viewCount + :delta where s.suggestionId = :id")
    int incrementViewCount(@Param("id") Long suggestionId, @Param("delta") int delta);

    // 공개글 전체 + 사용자 본인의 비공개글까지 포함해 목록을 조회한다
    @EntityGraph(attributePaths = "user")
    @Query("select s from Suggestion s where s.isPrivate = false or s.user.userId = :userId")
    Page<Suggestion> findVisibleByUserId(@Param("userId") Long userId, Pageable pageable);
}
