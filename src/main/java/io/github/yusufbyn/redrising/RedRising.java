package io.github.yusufbyn.redrising;

import io.github.yusufbyn.redrising.commande.CommandesDebug;
import io.github.yusufbyn.redrising.couleur.CapacitesActives;
import io.github.yusufbyn.redrising.couleur.CapacitesPassives;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.Naissance;
import io.github.yusufbyn.redrising.couleur.RestrictionsCouleur;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Contrebande;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.ObjetsRebelles;
import io.github.yusufbyn.redrising.jalons.EffetsMonde;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.jalons.MoteurJalons;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.monde.MineLykos;
import io.github.yusufbyn.redrising.monde.ObjetsMine;
import io.github.yusufbyn.redrising.monde.PosteSociete;
import io.github.yusufbyn.redrising.monde.RegionsMars;
import io.github.yusufbyn.redrising.monde.SourceBiomesMars;
import io.github.yusufbyn.redrising.monde.Vue;
import io.github.yusufbyn.redrising.pnj.PatrouillePnj;
import io.github.yusufbyn.redrising.pnj.PnjEssentiels;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Point d'entrée commun (serveur et client). */
public class RedRising implements ModInitializer {
    public static final String MOD_ID = "redrising";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Red Rising : initialisation");
        ObjetsRebelles.init();
        Boutiques.init();
        CouleurJoueur.init();
        Naissance.init();
        CapacitesPassives.init();
        RestrictionsCouleur.init();
        CapacitesActives.init();
        JaugesJoueur.init();
        ActionsFaction.init();
        PosteSociete.init();
        CelluleRising.init();
        PatrouillePnj.init();
        PnjEssentiels.init();
        TraqueGris.init();
        Vue.init();
        Contrebande.init();
        EtatMondeServeur.init();
        HorlogeMonde.init();
        MoteurJalons.init();
        EffetsMonde.init();
        RegionsMars.init();
        SourceBiomesMars.init();
        MineLykos.init();
        ObjetsMine.init();
        CommandesDebug.init();
    }
}
