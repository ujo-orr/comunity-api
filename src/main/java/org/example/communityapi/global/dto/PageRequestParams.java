package org.example.communityapi.global.dto;

import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.springframework.data.domain.PageRequest;

public final class PageRequestParams {
    private PageRequestParams() {}

    public static PageRequest of(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return PageRequest.of(page, size);
    }
}
