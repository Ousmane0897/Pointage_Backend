package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Résout les noms des agences référencées par des pointages.
 * <p>
 * Le pointage ne stocke que l'{@code agenceId} (pas de duplication d'état) : le nom est
 * résolu <b>à la lecture</b>, et un renommage d'agence se reflète donc partout. ⚠ Une
 * seule requête par page ({@code findAllById}), jamais une par ligne.
 */
@Component
@RequiredArgsConstructor
public class AgenceNomResolver {

    private final SiteClientRepository siteClientRepository;

    /** {@code id → nom} ; les ids nuls sont ignorés, une agence supprimée est absente. */
    public Map<String, String> noms(Collection<String> agenceIds) {
        Set<String> ids = agenceIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> noms = new HashMap<>();
        for (SiteClient s : siteClientRepository.findAllById(ids)) {
            noms.put(s.getId(), s.getNom());
        }
        return noms;
    }
}
