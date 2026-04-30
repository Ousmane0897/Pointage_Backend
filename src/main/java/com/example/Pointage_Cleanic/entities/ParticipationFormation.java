package com.example.Pointage_Cleanic.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "participations_formation")
@CompoundIndexes({
        @CompoundIndex(name = "session_employe_idx",
                def = "{'sessionId': 1, 'employeId': 1}", unique = true)
})
public class ParticipationFormation {

    @Id
    private String id;

    @Indexed
    private String sessionId;

    @Indexed
    private String employeId;

    private String matricule;
    private String nom;
    private String prenom;
    private String departement;

    private boolean present;
    private boolean completee;
    private boolean attestationGeneree;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateAttestation;
}