package com.WWU.CyberEnvironment.BLE.repository.models;

public class RegistrationRequestDto {
    String email;
    String password1;
    String password2;

    public RegistrationRequestDto(String email, String password1, String password2) {
        this.email = email;
        this.password1 = password1;
        this.password2 = password2;
    }
}