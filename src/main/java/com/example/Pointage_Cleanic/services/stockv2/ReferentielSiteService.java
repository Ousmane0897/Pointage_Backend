package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Accès LECTURE SEULE aux Sites Clients du module Terrain pour le module Stock v2.
 * Sert à valider un siteId et à dénormaliser le nom du site. N'écrit jamais dans
 * la collection {@code sites_clients}.
 */
@Service
@RequiredArgsConstructor
public class ReferentielSiteService {

    private final SiteClientRepository siteClientRepository;

    /** Nom du site, ou null si l'id est null/introuvable (dénormalisation tolérante). */
    public String nomDuSite(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            return null;
        }
        return siteClientRepository.findById(siteId)
                .map(SiteClient::getNom)
                .orElse(null);
    }

    /** Valide qu'un site existe ; lève IllegalArgumentException (-> 400) sinon. */
    public SiteClient valideEtCharge(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("siteId obligatoire");
        }
        return siteClientRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site introuvable : " + siteId));
    }
}
