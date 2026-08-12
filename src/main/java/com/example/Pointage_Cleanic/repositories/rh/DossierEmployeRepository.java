package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DossierEmployeRepository extends MongoRepository<DossierEmploye, String> {

    Optional<DossierEmploye> findByMatricule(String matricule);

    boolean existsByMatricule(String matricule);

    // Résolution du pointage : le codeSecret 4 chiffres envoyé par le mobile
    // correspond à l'agentId du dossier.
    Optional<DossierEmploye> findByAgentId(String agentId);

    boolean existsByAgentId(String agentId);

    // Lookup en une seule requête pour l'import bulk (éviter les N+1
    // sur la vérification d'unicité des matricules / agentId).
    List<DossierEmploye> findByMatriculeIn(Collection<String> matricules);

    List<DossierEmploye> findByAgentIdIn(Collection<String> agentIds);

    List<DossierEmploye> findByStatut(StatutDossierEmploye statut);

    Page<DossierEmploye> findByStatut(StatutDossierEmploye statut, Pageable pageable);

    List<DossierEmploye> findByStatutIn(List<StatutDossierEmploye> statuts);

    // Subordonnés d'un employé donné (organigramme, file de validation N1 des congés)
    List<DossierEmploye> findBySuperieurHierarchiqueId(String superieurHierarchiqueId);

    boolean existsBySuperieurHierarchiqueId(String superieurHierarchiqueId);

    /**
     * Résolution du compte de connexion (email du JWT) vers le dossier employé.
     * Renvoie une liste car le champ `email` n'est pas contraint unique en base :
     * l'appelant tranche et journalise le doublon éventuel.
     */
    List<DossierEmploye> findByEmailIgnoreCase(String email);

    // Stock v2 7.4 : repérage du « Responsable Achats » (validateur des bons)
    List<DossierEmploye> findByPosteIgnoreCase(String poste);

    List<DossierEmploye> findByDepartementIgnoreCase(String departement);
}