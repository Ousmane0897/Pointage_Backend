package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DemandeCongeRepository extends MongoRepository<DemandeConge, String> {

    List<DemandeConge> findByEmployeId(String employeId);

    List<DemandeConge> findByStatut(StatutDemande statut);

    List<DemandeConge> findByEmployeIdAndStatut(String employeId, StatutDemande statut);

    List<DemandeConge> findByType(TypeConge type);

    // Congés qui couvrent une date donnée (dateDebut <= date <= dateFin)
    List<DemandeConge> findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
            StatutDemande statut, LocalDate date, LocalDate dateSame);

    // ─── Circuit de validation à 3 niveaux ──────────────────────────────────
    // Requêtes Mongo dédiées : la file de validation ne doit pas passer par le
    // filtrage en mémoire de DemandeCongeService.searchDemandes.

    Page<DemandeConge> findByEmployeIdOrderByDateDemandeDesc(String employeId, Pageable pageable);

    /** File du niveau 1 : demandes des subordonnés directs de l'appelant. */
    Page<DemandeConge> findBySuperieurHierarchiqueIdAndStatutInOrderByDateDemandeDesc(
            String superieurHierarchiqueId, Collection<StatutDemande> statuts, Pageable pageable);

    long countBySuperieurHierarchiqueIdAndStatutIn(
            String superieurHierarchiqueId, Collection<StatutDemande> statuts);

    /** Files des niveaux 2 et 3 : filtrage par statut seul. */
    Page<DemandeConge> findByStatutInOrderByDateDemandeDesc(
            Collection<StatutDemande> statuts, Pageable pageable);

    long countByStatutIn(Collection<StatutDemande> statuts);

    List<DemandeConge> findByStatutIn(Collection<StatutDemande> statuts);
}