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

    // Employés actuellement en période d'essai (pour alertes)
    List<DossierEmploye> findByStatutAndDureeEssaiMoisIsNotNull(StatutDossierEmploye statut);

    // Subordonnés d'un employé donné (organigramme)
    List<DossierEmploye> findBySuperieurHierarchiqueId(String superieurHierarchiqueId);
}