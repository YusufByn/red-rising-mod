package io.github.yusufbyn.redrising.monde;

/**
 * Le froid selon la latitude (G7.1.2, GDD section 7) : logique pure (testée), lue par {@link SourceBiomesMars}. Les deux
 * pôles sont gelés au-delà de {@link PlanMars#LATITUDE_POLES} ; avant, une bande de {@link PlanMars#TRANSITION_POLES}
 * blocs où le froid gagne peu à peu : pas de mur.
 */
public final class Latitude {
    /** Ce que devient le biome de Minecraft à cet endroit. */
    public enum Climat {
        /** Inchangé. */
        NORMAL,
        /** Remplacé par un biome froid de même genre (la bande de transition). */
        FROID,
        /** Remplacé par un biome gelé (les pôles). */
        GELE
    }

    private Latitude() {
    }

    /**
     * Dans la bande de transition, la part de froid monte de 0 à 1 avec la latitude. Quels endroits basculent d'abord :
     * selon un bruit de Minecraft à cet endroit, l'« étrangeté » ({@code weirdness}), qui varie sur une centaine de blocs
     * et change avec la graine. Des taches de froid qui s'étendent, avec des bords naturels, plutôt qu'un bruit de points.
     * (La température de Minecraft varie sur un millier de blocs : sur une même latitude, elle peut être chaude partout, et
     * le froid arriverait d'un coup.)
     *
     * @param z         la coordonnée nord-sud, en blocs
     * @param variation le bruit à cet endroit, entre -10 000 et 10 000 ({@code Climate.TargetPoint.weirdness})
     */
    public static Climat climat(int z, long variation) {
        int latitude = Math.abs(z);
        if (latitude >= PlanMars.LATITUDE_POLES) {
            return Climat.GELE;
        }
        int debut = debutTransition();
        if (latitude < debut) {
            return Climat.NORMAL;
        }
        double part = (double) (latitude - debut) / PlanMars.TRANSITION_POLES;
        double froid = Math.clamp((variation + 10_000) / 20_000.0, 0, 1);
        return froid < part ? Climat.FROID : Climat.NORMAL;
    }

    /** Latitude où commence la bande de transition. */
    public static int debutTransition() {
        return PlanMars.LATITUDE_POLES - PlanMars.TRANSITION_POLES;
    }
}
