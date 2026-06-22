package com.smartfeed.data.model;

import java.util.List;

public final class RecommendationPageDto {

    private List<RecommendationDto> items;
    private int total;
    private int limit;
    private int offset;

    public List<RecommendationDto> getItems() {
        return items;
    }

    public int getTotal() {
        return total;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }
}
