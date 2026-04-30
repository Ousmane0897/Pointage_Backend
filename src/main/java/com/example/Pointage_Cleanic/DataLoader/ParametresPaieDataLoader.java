package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.entities.ParametresPaie;
import com.example.Pointage_Cleanic.entities.TrancheIr;
import com.example.Pointage_Cleanic.repositories.ParametresPaieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ParametresPaieDataLoader implements CommandLineRunner {

    private final ParametresPaieRepository parametresPaieRepository;

    @Override
    public void run(String... args) {
        if (parametresPaieRepository.count() > 0) {
            return;
        }

        ParametresPaie params = ParametresPaie.builder()
                .tauxIpresGeneralSalarie(0.056)
                .tauxIpresGeneralEmployeur(0.084)
                .plafondIpresGeneral(432_000L)
                .tauxIpresComplementaireSalarie(0.024)
                .tauxIpresComplementaireEmployeur(0.036)
                .plafondIpresComplementaire(1_296_000L)
                .tauxCssPrestationsFamilialesEmployeur(0.07)
                .plafondCss(63_000L)
                .tauxAtMpDefaut(0.01)
                .montantTrimfMensuel(900L)
                .joursOuvrablesStandardMois(22)
                .baremeIr(List.of(
                        TrancheIr.builder().borneMin(0L).borneMax(630_000L).taux(0.0).build(),
                        TrancheIr.builder().borneMin(630_001L).borneMax(1_500_000L).taux(0.20).build(),
                        TrancheIr.builder().borneMin(1_500_001L).borneMax(4_000_000L).taux(0.30).build(),
                        TrancheIr.builder().borneMin(4_000_001L).borneMax(8_000_000L).taux(0.35).build(),
                        TrancheIr.builder().borneMin(8_000_001L).borneMax(13_500_000L).taux(0.37).build(),
                        TrancheIr.builder().borneMin(13_500_001L).borneMax(null).taux(0.40).build()
                ))
                .dateModification(Instant.now())
                .build();

        parametresPaieRepository.save(params);
        System.out.println("Paramètres de paie par défaut créés (taux Sénégal)");
    }
}