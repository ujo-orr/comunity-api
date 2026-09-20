package org.example.communityapi.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<PostAttachment> findByPostId(Long postId);
    Optional<PostAttachment> findByIdAndPostId(Long id, Long postId);

    @Query("select attachment.storageKey from PostAttachment attachment where attachment.storageKey in :storageKeys")
    List<String> findExistingStorageKeys(@Param("storageKeys") List<String> storageKeys);
}
