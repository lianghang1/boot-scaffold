package com.sfi.scaffold.common.util;

import com.google.common.base.CaseFormat;

/**
 * 字符串工具唯一入口：继承 commons-lang3 的 {@code org.apache.commons.lang3.StringUtils}
 * （全量 250+ 静态方法穿透可用），仅追加 lang3/guava 都没有的脚手架自有语义。
 *
 * <p>为什么继承而不是直接用 lang3：统一 import 入口——团队判空/取默认值/驼峰转换
 * 只 import 本类一个来源，杜绝「同一件事四个 import」（JDK/Spring/lang3/hutool 各来一个）
 * 的风格漂移。import 唯一性由 ArchUnit 规则强制（见 scaffold-starter-test 的
 * {@code ScaffoldArchRules}），违规构建失败。</p>
 *
 * <p>已知代价（接受）：IDE 点进静态方法跳转的是 lang3 源码，类名显示为本类；
 * 本类与 lang3 同名，跨项目 review 时注意 import 来源——但 ArchUnit 保证脚手架内
 * 只存在本类这一个来源，歧义已被消灭。</p>
 *
 * <p>自有方法准则：只放「lang3/guava 都没有且脚手架内高频」的方法；
 * 纯转发 lang3 已有能力的一律不加。</p>
 *
 * @author lianghang
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {

    private StringUtils() {
        // 静态工具类：lang3 的设计允许子类化以统一入口，但本类不可实例化
    }

    /** 驼峰 → 下划线（userName → user_name），委托 guava CaseFormat */
    public static String camelToSnake(String text) {
        return text == null ? null
                : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, text);
    }

    /** 下划线 → 驼峰（user_name → userName），委托 guava CaseFormat */
    public static String snakeToCamel(String text) {
        return text == null ? null
                : CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, text);
    }

    /**
     * {@code {}} 占位符格式化（顺序替换，与 hutool StrUtil.format / SLF4J 同风格）：
     * {@code format("用户{}不存在: {}", "张三", 42L) → "用户张三不存在: 42"}。
     *
     * <p>占位符多于参数时多余占位符原样保留；参数多于占位符时多余参数忽略。
     * 与 JDK {@code MessageFormat} 的区别：不解析单引号/日期模式，纯文本替换。</p>
     */
    public static String format(String template, Object... args) {
        if (template == null || template.isEmpty() || args == null || args.length == 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + args.length * 8);
        int argIndex = 0;
        int from = 0;
        int idx;
        while (argIndex < args.length && (idx = template.indexOf("{}", from)) >= 0) {
            sb.append(template, from, idx).append(args[argIndex++]);
            from = idx + 2;
        }
        sb.append(template, from, template.length());
        return sb.toString();
    }
}
