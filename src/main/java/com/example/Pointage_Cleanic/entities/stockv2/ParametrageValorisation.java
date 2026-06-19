package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Paramétrage global de la valorisation (Stock v2 7.6). Singleton : un unique document
 * d'id fixe {@link #SINGLETON_ID}. Porte la méthode de valorisation par défaut appliquée
 * aux produits sans override explicite.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_parametrage_valorisation")
public class ParametrageValorisation {

    public static final String SINGLETON_ID = "GLOBAL";

    @Id
    private String id;

    private MethodeValorisation methodeDefaut;

    private LocalDateTime updatedAt;
    /** Utilisateur ayant modifié le paramétrage, dénormalisé depuis le JWT. */
    private String updatedBy;
}
