package com.zhaoyichi.devplatformbackend.common;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页数据结构。
 */
@Data
public class PageResult<T> {
    private List<T> list;
    private long total;
    private long page;
    private long pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(list == null ? Collections.<T>emptyList() : list);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }
}
