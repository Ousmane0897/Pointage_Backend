package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.entities.Planification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class  PlanificationDto {
    private String id;
    private String prenomNom;
    private String codeSecret;
    private String nomSite;
    private String[] siteDestination;
    private String personneRemplacee;
    private boolean matin;
    private boolean apresMidi;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String heureDebut;
    private String heureFin;
    private String commentaires;
    private String statut;
    private LocalDate dateCreation;
    private String motifAnnulation;
    
    private long joursRestants;  // ✅ Calculé dynamiquement


    // Convertir une entité en DTO
   public static PlanificationDto fromEntity(Planification plan) {
        PlanificationDto dto = new PlanificationDto();
        dto.setId(plan.getId());
        dto.setPrenomNom(plan.getPrenomNom());
        dto.setCodeSecret(plan.getCodeSecret());
        dto.setNomSite(plan.getNomSite());
        dto.setSiteDestination(plan.getSiteDestination());
        dto.setPersonneRemplacee(plan.getPersonneRemplacee());
        dto.setMatin(plan.isMatin());
        dto.setApresMidi(plan.isApresMidi());
        dto.setDateDebut(plan.getDateDebut() != null ? convertToLocalDate(plan.getDateDebut()) : null);
        dto.setDateFin(plan.getDateFin() != null ? convertToLocalDate(plan.getDateFin()) : null);
        dto.setHeureDebut(plan.getHeureDebut());
        dto.setHeureFin(plan.getHeureFin());
        dto.setCommentaires(plan.getCommentaires());
        dto.setStatut(plan.getStatut() != null ? plan.getStatut().name() : null);
        dto.setDateCreation(plan.getDateCreation());
        dto.setJoursRestants(plan.getJoursRestants()); // ✅ Calcul dynamique
        dto.setMotifAnnulation(plan.getMotifAnnulation()); // ✅ ICI !
        return dto;
    }

    /**
     * Convertit java.util.Date en java.time.LocalDate
     */
    public static LocalDate convertToLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

}
