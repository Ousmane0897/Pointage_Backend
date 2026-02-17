package com.example.Pointage_Cleanic.Dto;

import lombok.Data;

import java.util.List;

@Data
public class GeocodeResponse {
    private List<Result> results;
    private String status;

    @Data
    public static class Result {
        private String formatted_address;
    }
}

