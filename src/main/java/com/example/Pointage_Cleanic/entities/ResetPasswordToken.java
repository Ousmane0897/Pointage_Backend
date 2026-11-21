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
    /** Hash BCrypt du code à 6 chiffres (jamais le code en clair). */
    private String token;
    private LocalDateTime expiration;
    /** Essais de validation échoués ; au 5ᵉ, le code est supprimé. Absent des anciens documents → 0. */
    private int tentatives;
}

