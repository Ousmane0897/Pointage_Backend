package spikemongock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * État partagé entre le test du spike et ses ChangeUnits (même JVM).
 * Package hors de {@code com.example.Pointage_Cleanic} : jamais scanné par les autres tests.
 */
public final class SpikeEtat {

    /** Passe à vrai quand l'application a émis ApplicationReadyEvent (prête à servir). */
    public static volatile boolean applicationPrete;

    /** Valeur de {@link #applicationPrete} relevée pendant l'exécution d'une ChangeUnit. */
    public static volatile Boolean pretePendantMigration;

    /** Nombre d'exécutions effectives des ChangeUnits « ok ». */
    public static final AtomicInteger executions = new AtomicInteger();

    /** Ouvert quand une ChangeUnit « ok » commence (le verrou Mongock est alors tenu). */
    public static volatile CountDownLatch migrationCommencee = new CountDownLatch(1);

    /** Durée pendant laquelle la ChangeUnit « ok » garde la main (test du verrou). */
    public static volatile long pauseMs;

    private SpikeEtat() {
    }

    public static void reinitialiser() {
        applicationPrete = false;
        pretePendantMigration = null;
        executions.set(0);
        migrationCommencee = new CountDownLatch(1);
        pauseMs = 0;
    }
}
