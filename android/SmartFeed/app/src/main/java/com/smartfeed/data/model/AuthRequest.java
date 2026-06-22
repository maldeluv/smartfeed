package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

public final class AuthRequest {

    private final String email;
    private final String password;

    @SerializedName("full_name")
    private final String fullName;

    public AuthRequest(String email, String password) {
        this(email, null, password);
    }

    public AuthRequest(String email, String fullName, String password) {
        this.email = email;
        this.fullName = fullName;
        this.password = password;
    }

    public static AuthRequest forLogin(String email, String password) {
        return new AuthRequest(email, password);
    }

    public static AuthRequest forRegistration(String email, String fullName, String password) {
        return new AuthRequest(email, fullName, password);
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }
}
