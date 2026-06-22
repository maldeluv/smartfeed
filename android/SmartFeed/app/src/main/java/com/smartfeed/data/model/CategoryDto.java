package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

public final class CategoryDto {

    private long id;
    private String name;
    private String slug;
    private String description;

    @SerializedName(value = "is_subscribed", alternate = {"isSubscribed"})
    private Boolean subscribed;

    private CategoryDto(CategoryDto source, boolean subscribed) {
        id = source.id;
        name = source.name;
        slug = source.slug;
        description = source.description;
        this.subscribed = subscribed;
    }

    public static CategoryDto fromCache(
            long id,
            String name,
            String slug,
            String description,
            boolean subscribed
    ) {
        CategoryDto category = new CategoryDto();
        category.id = id;
        category.name = name;
        category.slug = slug;
        category.description = description;
        category.subscribed = subscribed;
        return category;
    }

    private CategoryDto() {
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSubscribed() {
        return Boolean.TRUE.equals(subscribed);
    }

    public CategoryDto withSubscribed(boolean value) {
        return new CategoryDto(this, value);
    }
}
