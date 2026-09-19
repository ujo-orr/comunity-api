package org.example.communityapi.attachment;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorage {
    String store(Long postId, MultipartFile file);
    Resource load(String storageKey);
    void deleteQuietly(String storageKey);
}
