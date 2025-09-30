package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.entities.Planification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanificationDto {
    private String id;
    private String prenomNom;
    private String codeSecret;
    private String nomSite;
    private String[] siteDestination;
    private String personneRemplacee;
    private boolean matin;
    private boolean apresMidi;
    private String dateDebut;
    private String dateFin;
    private String heureDebut;
    private String heureFin;
    private String commentaires;
    private String statut;
    private String dateCreation;
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
        dto.setDateDebut(plan.getDateDebut() != null ? plan.getDateDebut().toString() : null);
        dto.setDateFin(plan.getDateFin() != null ? plan.getDateFin().toString() : null);
        dto.setHeureDebut(plan.getHeureDebut());
        dto.setHeureFin(plan.getHeureFin());
        dto.setCommentaires(plan.getCommentaires());
        dto.setStatut(plan.getStatut() != null ? plan.getStatut().name() : null);
        dto.setDateCreation(plan.getDateCreation());
        dto.setJoursRestants(plan.getJoursRestants()); // ✅ Calcul dynamique
        dto.setMotifAnnulation(plan.getMotifAnnulation()); // ✅ ICI !
        return dto;
    }

}
