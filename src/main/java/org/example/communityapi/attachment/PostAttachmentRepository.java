package org.example.communityapi.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<PostAttachment> findByPostId(Long postId);
    Optional<PostAttachment> findByIdAndPostId(Long id, Long postId);
}
