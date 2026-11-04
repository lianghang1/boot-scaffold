package com.sfi.scaffold.common.validation;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.metadata.BeanDescriptor;
import java.util.Set;

/**
 * 程序化校验入口：非 Controller 入参（内部方法、消息消费者、任务参数）
 * 复用 Bean Validation 注解，违规抛 {@link BizException} 统一转响应。
 *
 * <p>Validator 单例懒加载；对外只暴露两个方法：校验通过静默返回，
 * 失败抛首条违规消息（@Valid 级联、分组均可传）。</p>
 *
 * @author lianghang
 */
public final class ValidationUtils {

    private static class Holder {
        private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
        private static final Validator VALIDATOR = FACTORY.getValidator();
    }

    private ValidationUtils() {
    }

    /** 校验对象，违规抛 1000 请求参数错误（消息为首条违规）。 */
    public static <T> void validate(T object, Class<?>... groups) {
        Set<ConstraintViolation<T>> violations = validateAll(object, groups);
        if (!violations.isEmpty()) {
            throw BizException.of(GlobalErrorCode.BAD_REQUEST,
                    violations.iterator().next().getMessage());
        }
    }

    /** 返回全部违规项（需要自定义处理时用）。 */
    public static <T> Set<ConstraintViolation<T>> validateAll(T object, Class<?>... groups) {
        return Holder.VALIDATOR.validate(object, groups);
    }

    /** 该类上是否有约束声明（避免对无约束对象做无谓校验）。 */
    public static boolean hasConstraints(Class<?> clazz) {
        BeanDescriptor descriptor = Holder.VALIDATOR.getConstraintsForClass(clazz);
        return descriptor.isBeanConstrained();
    }
}
