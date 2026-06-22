package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

public final class UserDto {

    private long id;
    private String email;

    @SerializedName("full_name")
    private String fullName;

    private String role;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("last_login_at")
    private String lastLoginAt;

    @SerializedName("is_active")
    private boolean active;

    public long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isActive() {
        return active;
    }
}
