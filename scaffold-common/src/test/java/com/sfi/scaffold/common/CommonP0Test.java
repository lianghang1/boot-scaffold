package com.sfi.scaffold.common;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.common.util.BizAssert;
import com.sfi.scaffold.common.util.ServletUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonP0Test {

    @Test
    void bizAssertPlaceholderMessage() {
        BizException e = assertThrows(BizException.class, () ->
                BizAssert.isTrue(false, GlobalErrorCode.BAD_REQUEST, "用户{}不存在", "张三"));
        assertEquals("用户张三不存在", e.getMessage());
        assertEquals(GlobalErrorCode.BAD_REQUEST, e.getErrorCode());
    }

    @Test
    void bizAssertParamPlaceholder() {
        BizException e = assertThrows(BizException.class, () ->
                BizAssert.param(1 > 2, "金额{}必须大于零", -1));
        assertEquals("金额-1必须大于零", e.getMessage());
    }

    @Test
    void bizExceptionPlaceholderConstructor() {
        assertEquals("订单A1已取消",
                new BizException(GlobalErrorCode.BAD_REQUEST, "订单{}已取消", "A1").getMessage());
        assertEquals("订单{}已取消",
                new BizException(GlobalErrorCode.BAD_REQUEST, "订单{}已取消").getMessage());
    }

    @Test
    void servletUtilsWriteJSON() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletUtils.writeJSON(response, java.util.Collections.singletonMap("code", 0));
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"code\":0"));
    }

    @Test
    void servletUtilsWriteErrorProducesJsonBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletUtils.writeError(response, new IllegalStateException("boom"));
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("code"));
    }

    @Test
    void servletUtilsHeaderAndAjax() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Token", "abc");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        assertEquals("abc", ServletUtils.getHeader(request, "X-Token", "def"));
        assertEquals("def", ServletUtils.getHeader(request, "X-Missing", "def"));
        assertTrue(ServletUtils.isAjaxRequest(request));
        assertFalse(ServletUtils.isAjaxRequest(new MockHttpServletRequest()));
    }
}
