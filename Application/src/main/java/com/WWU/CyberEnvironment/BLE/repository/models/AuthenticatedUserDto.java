package com.WWU.CyberEnvironment.BLE.repository.models;

public class AuthenticatedUserDto {
    public String key;
    public int user;
    public String error;

    public AuthenticatedUserDto(String key, int user, String error){
        this.key = key;
        this.user = user;
        this.error = error;
    }
}
