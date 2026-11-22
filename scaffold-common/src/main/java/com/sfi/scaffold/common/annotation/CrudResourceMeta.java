package com.sfi.scaffold.common.annotation;

import java.util.Arrays;

/**
 * {@link CrudResource} 的静态读取与动作归约工具，供权限拦截器与审计切面共用，
 * 避免两处各自解析注解产生漂移。
 *
 * <p>动作由 controller 方法名判定：list/page→LIST，get→GET，
 * save/create/update→SAVE，delete/remove→DELETE。</p>
 *
 * @author lianghang
 */
public final class CrudResourceMeta {

    public enum Action {
        LIST, GET, SAVE, DELETE, NONE
    }

    private CrudResourceMeta() {
    }

    /** 从 handler 方法名归约动作。 */
    public static Action actionOf(String methodName) {
        if (methodName == null) {
            return Action.NONE;
        }
        String m = methodName.toLowerCase();
        if (m.equals("list") || m.equals("page") || m.equals("lists") || m.equals("query")) {
            return Action.LIST;
        }
        if (m.equals("get") || m.equals("detail") || m.equals("find")) {
            return Action.GET;
        }
        if (m.equals("save") || m.equals("create") || m.equals("update") || m.equals("edit")) {
            return Action.SAVE;
        }
        if (m.equals("delete") || m.equals("remove") || m.equals("batchdelete")) {
            return Action.DELETE;
        }
        return Action.NONE;
    }

    /** 取该动作的权限点；get 回退 list，delete 回退 save。 */
    public static String[] permissionsOf(CrudResource cr, Action action) {
        if (cr == null || action == Action.NONE) {
            return new String[0];
        }
        switch (action) {
            case LIST:
                return cr.listPerm();
            case GET:
                return cr.getPerm().length > 0 ? cr.getPerm() : cr.listPerm();
            case SAVE:
                return cr.savePerm();
            case DELETE:
                return cr.deletePerm().length > 0 ? cr.deletePerm() : cr.savePerm();
            default:
                return new String[0];
        }
    }

    /** 取审计操作名（按动作给默认中文名）。 */
    public static String operationOf(Action action) {
        switch (action) {
            case LIST: return "查询列表";
            case GET: return "查看详情";
            case SAVE: return "保存";
            case DELETE: return "删除";
            default: return "";
        }
    }

    /** 权限点是否有实际声明（空数组或全空串视为未声明）。 */
    public static boolean hasPermission(String[] perms) {
        if (perms == null || perms.length == 0) {
            return false;
        }
        return Arrays.stream(perms).anyMatch(p -> p != null && !p.trim().isEmpty());
    }
}
