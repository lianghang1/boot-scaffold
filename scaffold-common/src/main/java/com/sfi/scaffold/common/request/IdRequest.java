package com.sfi.scaffold.common.request;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 通用主键请求 DTO：通用 CRUD 的 POST /delete 请求体。
 *
 * @author lianghang
 */
public class IdRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "id 不能为空")
    private Long id;

    public IdRequest() {
    }

    public IdRequest(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
