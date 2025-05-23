package com.example.Pointage_Cleanic.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "sites")
public class Site {

   @Id
   private String id;
   private String nom;
   private String type; //banque ou appartement
   private String emplacement;
   private String nombreEmployes;
   private String cheffeSite;
   private String frequenceDuNettoyage;

}
