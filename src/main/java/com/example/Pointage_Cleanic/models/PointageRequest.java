package com.example.Pointage_Cleanic.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PointageRequest {

    private String codeSecret;
    private String deviceId;
    private Double latitude;
    private Double longitude;
}
