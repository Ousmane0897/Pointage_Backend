package com.example.Pointage_Cleanic.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    private String password;
    private boolean mustChangePassword = true;
    private String role;
}
