package com.example.Pointage_Cleanic.entities;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "files")
public class Stockage {
    @Id
    private String id;
    private String filename;
    private String path;
    private LocalDateTime date;
    private String utilisateur;
}