package com.sfi.scaffold.common.exception;

/**
 * 业务异常，业务代码中表达「可预期失败」的唯一方式。
 *
 * <p>只用于可预期的业务失败——不可预期的技术异常直接抛原始异常，
 * 不要包装成 BizException 掩盖堆栈；全局异常处理器会记录 warn 日志并转换为统一响应，
 * 业务代码无需自行 try/catch 后拼响应。</p>
 *
 * @author lianghang
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 仅传消息时的默认业务码，取通用请求错误码（1000）；HTTP 状态恒 200，业务码进响应体 R.code。 */
    public static final int DEFAULT_CODE = 1000;

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 单个字符串消息：业务码取 {@link #DEFAULT_CODE}，文案即 message。 */
    public BizException(String message) {
        this(DEFAULT_CODE, message);
    }

    /** 业务码 + 字符串消息：code 进响应体 R.code，不影响 HTTP 状态（恒 200）。 */
    public BizException(int code, String message) {
        super(message);
        this.errorCode = new SimpleErrorCode(code, message);
    }

    /** 使用自定义 message 覆盖错误码默认文案。 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * {@code {}} 占位符文案：{@code new BizException(code, "用户{}不存在", name)}。
     */
    public BizException(ErrorCode errorCode, String message, Object... args) {
        super(message == null ? null
                : com.sfi.scaffold.common.util.StringUtils.format(message, args));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 轻量业务码实现：承载裸 code + message，httpStatus 恒 -1（HTTP 状态由全局处理器固定为 200）。 */
    private static final class SimpleErrorCode implements ErrorCode {
        private final int code;
        private final String message;

        SimpleErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public int getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public int getHttpStatus() {
            return -1;
        }
    }

    /** 快捷抛出。 */
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    /** 快捷抛出（自定义文案）。 */
    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    /** 快捷抛出（{@code {}} 占位符文案）。 */
    public static BizException of(ErrorCode errorCode, String message, Object... args) {
        return new BizException(errorCode, message, args);
    }
}
