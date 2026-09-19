package org.example.communityapi.attachment;

import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

// 로컬과 S3에 같은 업로드 정책을 적용한다.
final class AttachmentFileValidator {
    private AttachmentFileValidator() {}

    static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || filename.length() > 255
                || filename.equals(".") || filename.equals("..")
                || filename.contains("/") || filename.contains("\\")
                || filename.chars().anyMatch(Character::isISOControl)
                || (file.getContentType() != null && file.getContentType().length() > 100)
                || file.getSize() > 10L * 1024 * 1024) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
    }

}
