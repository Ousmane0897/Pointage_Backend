package com.example.Pointage_Cleanic.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PointageRequest {

    @NotBlank(message = "Code secret manquant")
    private String codeSecret;

    @NotBlank(message = "Device ID manquant")
    private String deviceId;

    private Double latitude;
    private Double longitude;
}
