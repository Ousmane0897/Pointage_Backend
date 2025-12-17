package com.example.Pointage_Cleanic.entities;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "ResetPasswordToken")
public class ResetPasswordToken {

    @Id
    private String id;

    private String email;
    private String token; // Code OTP ou UUID
    private LocalDateTime expiration;

    // getters & setters
}

