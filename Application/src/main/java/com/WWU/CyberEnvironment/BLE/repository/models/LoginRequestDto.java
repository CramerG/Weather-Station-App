package com.WWU.CyberEnvironment.BLE.repository.models;

public class LoginRequestDto {
    String email;
    String password;

    public LoginRequestDto(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
