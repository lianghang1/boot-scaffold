package com.sfi.scaffold.common.request;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serializable;

/**
 * 分页请求参数基类。
 *
 * <p>{@link #PAGE_SIZE_NONE} 表达「不分页拉全量」语义（导出、批量后台任务用），
 * 区别于「分页但每页 0 条」。pageSize 默认上限 100，超出按参数错误拒绝——
 * 防御前端传超大页拖垮库；需要更大页面的场景走导出通道。</p>
 *
 * @author lianghang
 */
public class PageParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 不分页语义：pageSize 取该值时一次拉全量 */
    public static final Integer PAGE_SIZE_NONE = -1;

    /** 单页最大条数 */
    public static final Integer PAGE_SIZE_MAX = 100;

    /** 页码（1 基） */
    @Min(value = 1, message = "页码最小值为 1")
    private Integer pageNum = 1;

    /** 每页条数；{@link #PAGE_SIZE_NONE} 表示不分页 */
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize = 10;

    /** 是否不分页（pageSize == PAGE_SIZE_NONE）。 */
    public boolean isUnpaged() {
        return PAGE_SIZE_NONE.equals(pageSize);
    }

    /** 是否分页查询。 */
    public boolean isPaged() {
        return !isUnpaged();
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
