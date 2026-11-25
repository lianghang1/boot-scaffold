package com.sfi.scaffold.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BizException 新增构造：单字符串消息、业务码 + 消息。
 * 两种构造的 code 进响应体 R.code，httpStatus 恒 -1（HTTP 状态由全局处理器固定 200）。
 *
 * @author lianghang
 */
class BizExceptionTest {

    @Test
    void singleMessage_usesDefaultCode() {
        BizException e = new BizException("业务失败");
        assertEquals("业务失败", e.getMessage());
        assertEquals(BizException.DEFAULT_CODE, e.getErrorCode().getCode());
        assertEquals(-1, e.getErrorCode().getHttpStatus());
    }

    @Test
    void codeAndMessage_carriedInErrorCode() {
        BizException e = new BizException(6004, "平台不支持分片");
        assertEquals(6004, e.getErrorCode().getCode());
        assertEquals("平台不支持分片", e.getErrorCode().getMessage());
        assertEquals("平台不支持分片", e.getMessage());
        assertEquals(-1, e.getErrorCode().getHttpStatus());
    }
}
