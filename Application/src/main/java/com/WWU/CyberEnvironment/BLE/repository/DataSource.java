package com.WWU.CyberEnvironment.BLE.repository;

import com.WWU.CyberEnvironment.BLE.repository.models.AuthenticatedUserDto;
import com.WWU.CyberEnvironment.BLE.repository.models.GetStationsResponseDto;

import org.json.JSONArray;

public interface DataSource {
    AuthenticatedUserDto userLogin(String email, String password);
    AuthenticatedUserDto userRegister(String email, String password1, String password2);
    GetStationsResponseDto getStations();
}
