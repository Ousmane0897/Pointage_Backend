package com.example.Pointage_Cleanic.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(collection = "login")
public class User {

    @Id
    private String id;
    private String email;
    /** Hash BCrypt : accepté en entrée, jamais sérialisé en sortie ni journalisé. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;
    private boolean mustChangePassword = true;
    private String role;
}
