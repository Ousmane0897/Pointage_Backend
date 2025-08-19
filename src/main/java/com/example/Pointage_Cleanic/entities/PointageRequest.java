package com.example.Pointage_Cleanic.entities;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "pointage_request")

public class PointageRequest {

    @Id
    private String id;
    private String employeeCode;
    private String deviceId;
    private Instant timestamp;


}
