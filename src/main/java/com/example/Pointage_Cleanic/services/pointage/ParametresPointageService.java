package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.Dto.pointage.ParametresPointageDto;
import com.example.Pointage_Cleanic.Enum.pointage.ModeHorsAffectation;
import com.example.Pointage_Cleanic.entities.pointage.ParametresPointage;
import com.example.Pointage_Cleanic.exception.PointageAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.pointage.ParametresPointageRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Paramètres du pointage kiosque — singleton get-or-create (patron
 * {@code ParametresCongesService}, et non {@code ParametresPaieService}, dont le 404 sur
 * base neuve bloquerait ici <b>tout pointage</b>).
 */
@Service
@RequiredArgsConstructor
public class ParametresPointageService {

    public static final ModeHorsAffectation HORS_AFFECTATION_DEFAUT = ModeHorsAffectation.AVERTIR;

    private static final Set<String> ROLES_ADMIN = Set.of("RH", "SUPERADMIN");

    private final ParametresPointageRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ParametresPointageDto getParametres() {
        return toDto(getOrCreate());
    }

    /**
     * Patch champ par champ, réservé RH / SUPERADMIN.
     * <p>⚠ Garde posée ici et non par annotation : le projet n'active pas
     * {@code @EnableMethodSecurity}, un {@code @PreAuthorize} serait ignoré en silence.
     */
    public ParametresPointageDto updateParametres(ParametresPointageDto dto) {
        if (!estAdministrateur()) {
            throw new PointageAccesRefuseException(
                    "Seuls les profils RH et super-administrateur peuvent modifier les paramètres de pointage.");
        }
        ParametresPointage entity = getOrCreate();
        if (dto.getHorsAffectation() != null) entity.setHorsAffectation(dto.getHorsAffectation());
        if (dto.getQrObligatoire() != null) entity.setQrObligatoire(dto.getQrObligatoire());
        entity.setDateModification(LocalDateTime.now(clock));
        entity.setModifieParId(currentUserProvider.currentUserId());
        entity.setModifieParNom(currentUserProvider.currentUserNom());
        return toDto(repository.save(entity));
    }

    /** RH ou SUPERADMIN — partagé avec l'administration des QR d'agence. */
    public boolean estAdministrateur() {
        String role = currentUserProvider.currentRole();
        return role != null && ROLES_ADMIN.contains(role);
    }

    /**
     * Singleton semé aux valeurs par défaut s'il est absent.
     * <p>⚠ Un champ nul (document semé par une version antérieure) retombe sur le
     * défaut : c'est le rôle de {@link #modeHorsAffectation(ParametresPointage)}.
     */
    public ParametresPointage getOrCreate() {
        return repository.findFirstByOrderByIdAsc().orElseGet(() -> repository.save(
                ParametresPointage.builder()
                        .horsAffectation(HORS_AFFECTATION_DEFAUT)
                        .qrObligatoire(false)
                        .dateModification(LocalDateTime.now(clock))
                        .modifieParNom("system")
                        .build()));
    }

    public static ModeHorsAffectation modeHorsAffectation(ParametresPointage p) {
        return p == null || p.getHorsAffectation() == null ? HORS_AFFECTATION_DEFAUT : p.getHorsAffectation();
    }

    public static boolean qrObligatoire(ParametresPointage p) {
        return p != null && Boolean.TRUE.equals(p.getQrObligatoire());
    }

    private ParametresPointageDto toDto(ParametresPointage e) {
        return ParametresPointageDto.builder()
                .horsAffectation(modeHorsAffectation(e))
                .qrObligatoire(qrObligatoire(e))
                .dateModification(e.getDateModification())
                .modifieParNom(e.getModifieParNom())
                .build();
    }
}
