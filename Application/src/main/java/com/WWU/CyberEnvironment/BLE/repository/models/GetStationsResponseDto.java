package com.WWU.CyberEnvironment.BLE.repository.models;

import java.util.List;

public class GetStationsResponseDto {
    public List<StationDto> stations;
    public String error;

    public GetStationsResponseDto(String error){
        this.error = error;
    }
}
