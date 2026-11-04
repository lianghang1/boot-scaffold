package com.sfi.scaffold.common.request;

import javax.validation.Valid;
import java.io.Serializable;

/**
 * 带排序的分页请求参数。
 *
 * @author lianghang
 */
public class SortablePageParam extends PageParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @Valid
    private SortingField sortingField;

    public SortingField getSortingField() {
        return sortingField;
    }

    public void setSortingField(SortingField sortingField) {
        this.sortingField = sortingField;
    }
}
