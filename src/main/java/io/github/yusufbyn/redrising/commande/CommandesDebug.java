package io.github.yusufbyn.redrising.commande;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.yusufbyn.redrising.couleur.Couleur;
import io.github.yusufbyn.redrising.couleur.CouleurJoueur;
import io.github.yusufbyn.redrising.couleur.ModeNaissance;
import io.github.yusufbyn.redrising.couleur.Naissance;
import io.github.yusufbyn.redrising.couleur.TestEnCours;
import io.github.yusufbyn.redrising.data.EtatMonde;
import io.github.yusufbyn.redrising.data.EtatMondeServeur;
import io.github.yusufbyn.redrising.factions.Acces;
import io.github.yusufbyn.redrising.factions.AchatsDuJour;
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Boutique;
import io.github.yusufbyn.redrising.factions.Boutiques;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.ChausseTrappes;
import io.github.yusufbyn.redrising.factions.Contrebande;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.factions.Nuage;
import io.github.yusufbyn.redrising.factions.RegistreChausseTrappes;
import io.github.yusufbyn.redrising.jalons.Attitude;
import io.github.yusufbyn.redrising.jalons.Bilan;
import io.github.yusufbyn.redrising.jalons.ContributionDuJour;
import io.github.yusufbyn.redrising.jalons.Echeance;
import io.github.yusufbyn.redrising.jalons.EffetEnCours;
import io.github.yusufbyn.redrising.jalons.EffetsMonde;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Horloge;
import io.github.yusufbyn.redrising.jalons.HorlogeMonde;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.jalons.Jalon;
import io.github.yusufbyn.redrising.jalons.Jalons;
import io.github.yusufbyn.redrising.jalons.JalonsEvents;
import io.github.yusufbyn.redrising.jalons.Levier;
import io.github.yusufbyn.redrising.jalons.MortPersonnage;
import io.github.yusufbyn.redrising.jalons.MoteurJalons;
import io.github.yusufbyn.redrising.jalons.Personnage;
import io.github.yusufbyn.redrising.jalons.Personnages;
import io.github.yusufbyn.redrising.monde.CelluleRising;
import io.github.yusufbyn.redrising.monde.PlanMars;
import io.github.yusufbyn.redrising.monde.PosteSociete;
import io.github.yusufbyn.redrising.monde.Region;
import io.github.yusufbyn.redrising.monde.RegionsMars;
import io.github.yusufbyn.redrising.monde.Vue;
import io.github.yusufbyn.redrising.factions.Ronde;
import io.github.yusufbyn.redrising.factions.Traque;
import io.github.yusufbyn.redrising.pnj.PatrouillePnj;
import io.github.yusufbyn.redrising.pnj.TraqueGris;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import io.github.yusufbyn.redrising.pnj.PnjEssentiels;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Commandes de debug {@code /rr} (G2.5), réservées aux joueurs avec la triche activée.
 *
 * <p>Brigadier (le moteur de commandes de Minecraft) décrit une commande comme un arbre :
 * {@code literal} pour un mot fixe, {@code argument} pour une valeur saisie, {@code executes} pour l'action.
 * Les commandes visant un joueur prennent un {@code [joueur]} optionnel ; sans lui, elles visent celui qui tape.</p>
 */
public final class CommandesDebug {
    private static final DynamicCommandExceptionType VALEUR_INCONNUE = new DynamicCommandExceptionType(
            valeur -> Component.translatable("commande.redrising.inconnu", valeur));
    private static final DynamicCommandExceptionType JALON_ABSENT = new DynamicCommandExceptionType(
            jalon -> Component.translatable("commande.redrising.jalon.absent", jalon));
    private static final DynamicCommandExceptionType JALON_INCONNU = new DynamicCommandExceptionType(
            jalon -> Component.translatable("commande.redrising.jalon.inconnu", jalon));
    private static final DynamicCommandExceptionType JALON_DEJA_PASSE = new DynamicCommandExceptionType(
            jalon -> Component.translatable("commande.redrising.jalon.deja_passe", jalon));
    private static final DynamicCommandExceptionType EFFET_ABSENT = new DynamicCommandExceptionType(
            effet -> Component.translatable("commande.redrising.effet.absent", effet));
    private static final DynamicCommandExceptionType PERSONNAGE_INCONNU = new DynamicCommandExceptionType(
            personnage -> Component.translatable("commande.redrising.personnage.inconnu", personnage));
    private static final DynamicCommandExceptionType PERSONNAGE_MORT = new DynamicCommandExceptionType(
            personnage -> Component.translatable("commande.redrising.personnage.refuse_mort", personnage));
    private static final DynamicCommandExceptionType PAS_UN_VILLAGEOIS = new DynamicCommandExceptionType(
            entite -> Component.translatable("commande.redrising.pnj.pas_villageois", entite));
    private static final DynamicCommandExceptionType PAS_UN_COFFRE = new DynamicCommandExceptionType(
            pos -> Component.translatable("commande.redrising.coffre.absent", pos));
    private static final Direction[] HORIZONTALES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final DynamicCommandExceptionType PAS_DE_STRUCTURE = new DynamicCommandExceptionType(
            pos -> Component.translatable("commande.redrising.structure.absente", pos));
    private static final SimpleCommandExceptionType PNJ_REFUSE = new SimpleCommandExceptionType(
            Component.translatable("commande.redrising.pnj.refuse"));
    private static final SimpleCommandExceptionType PAS_DE_PLAN = new SimpleCommandExceptionType(
            Component.translatable("commande.redrising.region.aucun_plan"));
    private static final DynamicCommandExceptionType ZONE_ABSENTE = new DynamicCommandExceptionType(
            zone -> Component.translatable("commande.redrising.etat.zone.absente", zone));

    /** Une action qui vise un joueur ; peut échouer comme une commande. */
    @FunctionalInterface
    private interface ActionJoueur {
        int executer(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) throws CommandSyntaxException;
    }

    private CommandesDebug() {
    }

    /** Appelé depuis {@link io.github.yusufbyn.redrising.RedRising#onInitialize()}. */
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> enregistrer(dispatcher));
    }

    private static void enregistrer(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rr")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("couleur")
                        .then(Commands.literal("get").executes(ctx -> lireCouleur(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lireCouleur)))
                        .then(Commands.literal("set")
                                .then(argEnum("couleur", Couleur.values()).executes(ctx -> changerCouleur(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::changerCouleur)))))
                .then(Commands.literal("naissance")
                        .then(Commands.literal("get").executes(ctx -> lireNaissance(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lireNaissance)))
                        .then(Commands.literal("reset").executes(ctx -> relancerNaissance(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::relancerNaissance))))
                .then(Commands.literal("pnj")
                        .then(Commands.literal("spawn")
                                .then(argEnum("couleur", Couleur.values()).executes(CommandesDebug::creerPnj)))
                        .then(Commands.literal("get")
                                .then(Commands.argument("pnj", EntityArgument.entity()).executes(CommandesDebug::lirePnj)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("pnj", EntityArgument.entity())
                                        .then(argEnum("couleur", Couleur.values()).executes(CommandesDebug::changerPnj))))
                        .then(Commands.literal("camp")
                                .then(Commands.argument("pnj", EntityArgument.entity())
                                        .then(argEnum("camp", Camp.values()).executes(ctx -> changerCampPnj(ctx, lireEnum(ctx, "camp", Camp.values()))))
                                        .then(Commands.literal("aucun").executes(ctx -> changerCampPnj(ctx, null)))))
                        // Personnages canon (G6.4)
                        .then(Commands.literal("personnage")
                                .then(Commands.argument("pnj", EntityArgument.entity())
                                        .then(Commands.literal("aucun").executes(CommandesDebug::libererPnj))
                                        .then(Commands.argument("personnage", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Personnages.tous().stream().map(Personnage::id), builder))
                                                .executes(CommandesDebug::incarnerPnj))))
                        .then(Commands.literal("ko")
                                .then(Commands.argument("pnj", EntityArgument.entity()).executes(CommandesDebug::mettreKo)))
                        .then(Commands.literal("reveiller")
                                .then(Commands.argument("pnj", EntityArgument.entity()).executes(CommandesDebug::reveillerPnj))))
                .then(Commands.literal("personnage")
                        .then(Commands.literal("list").executes(CommandesDebug::listerPersonnages))
                        .then(Commands.literal("tuer")
                                .then(Commands.argument("pnj", EntityArgument.entity()).executes(CommandesDebug::tuerPersonnage)))
                        .then(Commands.literal("oublier")
                                .then(Commands.argument("personnage", StringArgumentType.word()).executes(CommandesDebug::oublierPersonnage)))
                        // Attitude envers un joueur (G6.5)
                        .then(Commands.literal("attitude")
                                .then(Commands.literal("get")
                                        .then(argPersonnageDeclare().executes(ctx -> lireAttitude(ctx, ctx.getSource().getPlayerOrException()))
                                                .then(argJoueur(CommandesDebug::lireAttitude))))
                                .then(Commands.literal("set")
                                        .then(argPersonnageDeclare()
                                                .then(Commands.argument("valeur", IntegerArgumentType.integer())
                                                        .executes(ctx -> changerAttitude(ctx, ctx.getSource().getPlayerOrException(), false))
                                                        .then(argJoueur((ctx, joueur) -> changerAttitude(ctx, joueur, false))))))
                                .then(Commands.literal("add")
                                        .then(argPersonnageDeclare()
                                                .then(Commands.argument("valeur", IntegerArgumentType.integer())
                                                        .executes(ctx -> changerAttitude(ctx, ctx.getSource().getPlayerOrException(), true))
                                                        .then(argJoueur((ctx, joueur) -> changerAttitude(ctx, joueur, true))))))))
                // Plan des régions de Mars (G7.1.1)
                .then(Commands.literal("region")
                        .then(Commands.literal("list").executes(CommandesDebug::listerRegions))
                        .then(Commands.literal("tp")
                                .then(argEnum("region", Region.values()).executes(ctx -> allerALaRegion(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::allerALaRegion)))))
                .then(Commands.literal("structure")
                        .then(Commands.literal("list").executes(CommandesDebug::listerStructures))
                        .then(Commands.literal("poste")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CommandesDebug::construirePoste)))
                        .then(Commands.literal("cellule")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(argEnum("direction", HORIZONTALES).executes(CommandesDebug::construireCellule))))
                        .then(Commands.literal("oublier")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CommandesDebug::oublierStructure)))
                        .then(Commands.literal("tomber")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CommandesDebug::faireTomber))))
                .then(Commands.literal("denonciation")
                        .then(Commands.literal("get").executes(ctx -> lireDenonciation(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lireDenonciation)))
                        .then(Commands.literal("reset").executes(ctx -> effacerDenonciation(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::effacerDenonciation))))
                .then(Commands.literal("patrouille")
                        .then(Commands.literal("creer")
                                .then(argPoint(1).then(argPoint(2).executes(ctx -> creerPatrouille(ctx, 2))
                                        .then(argPoint(3).executes(ctx -> creerPatrouille(ctx, 3))
                                                .then(argPoint(4).executes(ctx -> creerPatrouille(ctx, 4)))))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("pnj", EntityArgument.entity()).executes(CommandesDebug::lirePatrouille)))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("pnj", EntityArgument.entity()).executes(CommandesDebug::retirerPatrouille))))
                .then(Commands.literal("controle")
                        .then(Commands.literal("get").executes(ctx -> lireControle(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lireControle)))
                        .then(Commands.literal("reset").executes(ctx -> effacerControle(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::effacerControle))))
                .then(Commands.literal("traque")
                        .then(Commands.literal("get").executes(ctx -> lireTraque(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lireTraque)))
                        .then(Commands.literal("start").executes(ctx -> lancerTraque(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lancerTraque)))
                        .then(Commands.literal("stop").executes(ctx -> arreterTraque(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::arreterTraque)))
                        .then(Commands.literal("arreter").executes(ctx -> arreterJoueur(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::arreterJoueur))))
                .then(Commands.literal("boutique")
                        .then(Commands.literal("get")
                                .then(Commands.argument("pnj", EntityArgument.entity()).executes(CommandesDebug::lireBoutique)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("pnj", EntityArgument.entity())
                                        .then(argEnum("boutique", Boutique.values()).executes(ctx -> changerBoutique(ctx, lireEnum(ctx, "boutique", Boutique.values()))))
                                        .then(Commands.literal("aucune").executes(ctx -> changerBoutique(ctx, null)))))
                        .then(Commands.literal("achats")
                                .then(Commands.literal("get").executes(ctx -> lireAchats(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::lireAchats)))
                                .then(Commands.literal("reset").executes(ctx -> effacerAchats(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::effacerAchats)))))
                .then(Commands.literal("fumee")
                        .then(Commands.literal("list").executes(CommandesDebug::listerFumees))
                        .then(Commands.literal("clear").executes(CommandesDebug::dissiperFumees))
                        .then(Commands.argument("pos", Vec3Argument.vec3()).executes(CommandesDebug::enfumer)))
                .then(Commands.literal("chausse_trappes")
                        .then(Commands.literal("list").executes(ctx -> listerChausseTrappes(ctx, null))
                                .then(argJoueur(CommandesDebug::listerChausseTrappes)))
                        .then(Commands.literal("clear").executes(ctx -> effacerChausseTrappes(ctx, null))
                                .then(argJoueur(CommandesDebug::effacerChausseTrappes))))
                .then(Commands.literal("contrebande")
                        .then(Commands.literal("get").executes(ctx -> lireContrebande(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lireContrebande)))
                        .then(Commands.literal("reset").executes(ctx -> effacerContrebande(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::effacerContrebande))))
                .then(Commands.literal("coffre")
                        .then(Commands.literal("get")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CommandesDebug::lireCoffre)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(argEnum("camp", Camp.values()).executes(ctx -> changerCoffre(ctx, lireEnum(ctx, "camp", Camp.values()))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> changerCoffre(ctx, null)))))
                .then(Commands.literal("jauge")
                        .then(Commands.literal("get").executes(ctx -> lireJauges(ctx, ctx.getSource().getPlayerOrException()))
                                .then(argJoueur(CommandesDebug::lireJauges)))
                        .then(Commands.literal("set")
                                .then(argEnum("jauge", Jauge.values())
                                        .then(Commands.argument("valeur", IntegerArgumentType.integer())
                                                .executes(ctx -> changerJauge(ctx, ctx.getSource().getPlayerOrException(), false))
                                                .then(argJoueur((ctx, joueur) -> changerJauge(ctx, joueur, false))))))
                        .then(Commands.literal("add")
                                .then(argEnum("jauge", Jauge.values())
                                        .then(Commands.argument("valeur", IntegerArgumentType.integer())
                                                .executes(ctx -> changerJauge(ctx, ctx.getSource().getPlayerOrException(), true))
                                                .then(argJoueur((ctx, joueur) -> changerJauge(ctx, joueur, true)))))))
                .then(Commands.literal("jalon")
                        .then(Commands.literal("list").executes(CommandesDebug::listerJalons))
                        .then(Commands.literal("set")
                                .then(Commands.argument("jalon", StringArgumentType.word())
                                        .then(argEnum("issue", Issue.values()).executes(CommandesDebug::fixerJalon))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("jalon", StringArgumentType.word()).executes(CommandesDebug::effacerJalon)))
                        // Moteur des jalons (G6.2)
                        .then(Commands.literal("prochains").executes(CommandesDebug::listerProchains))
                        .then(Commands.literal("declencher")
                                .then(argJalonDeclare().executes(ctx -> declencherJalon(ctx, null))
                                        .then(argEnum("issue", Issue.values()).executes(ctx -> declencherJalon(ctx, lireEnum(ctx, "issue", Issue.values()))))))
                        .then(Commands.literal("effets")
                                .then(Commands.argument("jalon", StringArgumentType.word())
                                        .then(Commands.literal("appliques").executes(ctx -> marquerEffets(ctx, false)))
                                        .then(Commands.literal("en_attente").executes(ctx -> marquerEffets(ctx, true))))))
                // Effets des jalons (G6.3)
                .then(Commands.literal("effet")
                        .then(Commands.literal("list").executes(CommandesDebug::listerEffets))
                        .then(Commands.literal("poser")
                                .then(argEnum("levier", Levier.values())
                                        .then(Commands.argument("niveau", IntegerArgumentType.integer(0))
                                                .executes(ctx -> poserEffet(ctx, EffetEnCours.PERMANENT))
                                                .then(Commands.argument("ticks", LongArgumentType.longArg(1))
                                                        .executes(ctx -> poserEffet(ctx, LongArgumentType.getLong(ctx, "ticks")))))))
                        .then(Commands.literal("retirer")
                                .then(Commands.argument("id", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                EtatMondeServeur.get(ctx.getSource().getServer()).effets().keySet(), builder))
                                        .executes(CommandesDebug::retirerEffet)))
                        .then(Commands.literal("recus")
                                .then(Commands.literal("get").executes(ctx -> lireRecus(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::lireRecus)))
                                .then(Commands.literal("reset").executes(ctx -> effacerRecus(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::effacerRecus)))))
                .then(Commands.literal("etat").executes(CommandesDebug::lireEtat)
                        .then(Commands.literal("naissance")
                                .then(argEnum("mode", ModeNaissance.values()).executes(CommandesDebug::changerModeNaissance)))
                        .then(Commands.literal("epoque")
                                .then(argEnum("epoque", Epoque.values()).executes(CommandesDebug::changerEpoque)))
                        .then(Commands.literal("zone")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("zone", StringArgumentType.word())
                                                .then(argEnum("camp", Camp.values()).executes(CommandesDebug::fixerZone))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("zone", StringArgumentType.word()).executes(CommandesDebug::effacerZone))))
                        // Horloge du monde (G6.1)
                        .then(Commands.literal("temps")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("ticks", LongArgumentType.longArg(0)).executes(ctx -> changerTemps(ctx, false))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("ticks", LongArgumentType.longArg()).executes(ctx -> changerTemps(ctx, true)))))
                        .then(Commands.literal("presence")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("ticks", LongArgumentType.longArg(0)).executes(ctx -> changerPresence(ctx, false))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("ticks", LongArgumentType.longArg()).executes(ctx -> changerPresence(ctx, true)))))
                        .then(Commands.literal("soutien")
                                .then(compteurSoutien("aide"))
                                .then(compteurSoutien("tort")))
                        .then(Commands.literal("contribution")
                                .then(Commands.literal("get").executes(ctx -> lireContribution(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::lireContribution)))
                                .then(Commands.literal("reset").executes(ctx -> effacerContribution(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(argJoueur(CommandesDebug::effacerContribution))))));
    }

    /** {@code soutien aide|tort set|add <n>}. */
    private static LiteralArgumentBuilder<CommandSourceStack> compteurSoutien(String compteur) {
        return Commands.literal(compteur)
                .then(Commands.literal("set")
                        .then(Commands.argument("valeur", LongArgumentType.longArg(0)).executes(ctx -> changerSoutienCumule(ctx, compteur, false))))
                .then(Commands.literal("add")
                        .then(Commands.argument("valeur", LongArgumentType.longArg()).executes(ctx -> changerSoutienCumule(ctx, compteur, true))));
    }

    // --- Couleur ---

    private static int lireCouleur(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        Couleur couleur = CouleurJoueur.get(joueur);
        if (couleur == null) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.couleur.aucune", joueur.getDisplayName()), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.couleur.get", joueur.getDisplayName(), couleur.getDisplayName()), false);
        return couleur.ordinal() + 1;
    }

    private static int changerCouleur(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) throws CommandSyntaxException {
        Couleur couleur = lireEnum(ctx, "couleur", Couleur.values());
        // Pendant la naissance, la commande fait naître le joueur avec cette Couleur
        Naissance.donnerCouleur(joueur, couleur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.couleur.set", joueur.getDisplayName(), couleur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Naissance ---

    private static int lireNaissance(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        Couleur couleur = CouleurJoueur.get(joueur);
        TestEnCours test = Naissance.testEnCours(joueur);
        Component etat;
        if (couleur != null) {
            etat = Component.translatable("commande.redrising.naissance.nee", couleur.getDisplayName());
        } else if (test == null) {
            etat = Component.translatable("commande.redrising.naissance.aucune");
        } else {
            Component relance = Component.translatable(test.relanceUtilisee()
                    ? "commande.redrising.naissance.relance_utilisee" : "commande.redrising.naissance.relance_disponible");
            etat = test.resultat() == null
                    ? Component.translatable("commande.redrising.naissance.questions", relance)
                    : Component.translatable("commande.redrising.naissance.resultat", test.resultat().getDisplayName(), relance);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.naissance.get", joueur.getDisplayName(), etat), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Efface Couleur et test, et relance la naissance selon le mode du monde : l'écran de naissance revient. */
    private static int relancerNaissance(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        Naissance.renaitre(joueur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.naissance.reset", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- PNJ ---

    /** Un PNJ de la Couleur demandée, là où se tient celui qui tape la commande, tourné vers lui. */
    private static int creerPnj(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Couleur couleur = lireEnum(ctx, "couleur", Couleur.values());
        CommandSourceStack source = ctx.getSource();
        Villager pnj = PnjCouleur.creer(source.getLevel(), source.getPosition(), source.getRotation().y + 180, couleur);
        if (pnj == null) {
            throw PNJ_REFUSE.create();
        }
        source.sendSuccess(() -> Component.translatable("commande.redrising.pnj.spawn", couleur.getDisplayName(), metier(pnj)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int lirePnj(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        Couleur couleur = PnjCouleur.couleur(pnj);
        Component nomCouleur = couleur == null ? Component.translatable("commande.redrising.aucun") : couleur.getDisplayName();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.pnj.get", pnj.getDisplayName(), nomCouleur, metier(pnj),
                nomCamp(pnj.getAttached(ActionsFaction.CAMP))), false);
        return couleur == null ? 0 : couleur.ordinal() + 1;
    }

    private static int changerPnj(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        Couleur couleur = lireEnum(ctx, "couleur", Couleur.values());
        PnjCouleur.donnerCouleur(pnj, couleur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.pnj.set", pnj.getDisplayName(), couleur.getDisplayName(), metier(pnj)), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Camp secret d'un PNJ (G5.2) : un PNJ du Rising peut être dénoncé. {@code null} : aucun camp. */
    private static int changerCampPnj(CommandContext<CommandSourceStack> ctx, @Nullable Camp camp) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        if (camp == null) {
            pnj.removeAttached(ActionsFaction.CAMP);
        } else {
            pnj.setAttached(ActionsFaction.CAMP, camp);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.pnj.camp", pnj.getDisplayName(), nomCamp(camp)), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Structures (G5.3, G5.4) ---

    // --- Plan des régions de Mars (G7.1.1) ---

    /** Chaque région du plan : son centre et sa distance au point d'apparition ; ou pas de plan. */
    private static int listerRegions(CommandContext<CommandSourceStack> ctx) {
        PlanMars plan = RegionsMars.plan(ctx.getSource().getServer());
        if (plan == null) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.region.aucun_plan"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.region.apparition", plan.apparition().toShortString()), false);
        for (Region region : Region.values()) {
            BlockPos pos = plan.regions().get(region);
            long distance = Math.round(Math.hypot(pos.getX() - plan.apparition().getX(), pos.getZ() - plan.apparition().getZ()));
            ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.region.ligne", nom("region", region), pos.toShortString(), distance), false);
        }
        return plan.regions().size();
    }

    /** Téléporte le joueur au-dessus du centre de la région (génère les chunks de là-bas). */
    private static int allerALaRegion(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) throws CommandSyntaxException {
        Region region = lireEnum(ctx, "region", Region.values());
        PlanMars plan = RegionsMars.plan(ctx.getSource().getServer());
        if (plan == null) {
            throw PAS_DE_PLAN.create();
        }
        BlockPos centre = plan.regions().get(region);
        ServerLevel monde = ctx.getSource().getServer().overworld();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX(), centre.getZ());
        joueur.teleportTo(monde, centre.getX() + 0.5, y, centre.getZ() + 0.5, Set.of(), joueur.getYRot(), joueur.getXRot(), true);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.region.tp", joueur.getDisplayName(), nom("region", region),
                new BlockPos(centre.getX(), y, centre.getZ()).toShortString()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listerStructures(CommandContext<CommandSourceStack> ctx) {
        List<PosteSociete.Poste> postes = PosteSociete.postes(ctx.getSource().getServer());
        List<String> portes = postes.stream().map(poste -> poste.porte().toShortString()).toList();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.list", postes.size(),
                Acces.SEUIL_LOYAUTE_SOCIETE, positions(portes)), false);
        List<CelluleRising.Cellule> cellules = CelluleRising.cellules(ctx.getSource().getServer());
        List<String> entrees = cellules.stream().map(cellule -> cellule.entree().toShortString() + " (" + cellule.versSalle().getSerializedName()
                + (cellule.reperee() ? ", repérée, descente au tick " + cellule.descente() : "")
                + (cellule.sansFils() >= 0 ? ", sans Fils depuis le tick " + cellule.sansFils() : "") + ")").toList();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.list.cellules", cellules.size(),
                Acces.SEUIL_SOUTIEN_RISING, positions(entrees)), false);
        return postes.size() + cellules.size();
    }

    private static Component positions(List<String> positions) {
        return positions.isEmpty() ? Component.translatable("commande.redrising.aucun") : Component.literal(String.join(" ; ", positions));
    }

    /** Construit un poste de la Société, coin nord-ouest du sol à la position donnée (la porte est au nord). */
    private static int construirePoste(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, ctx.getSource().getServer().overworld(), "pos");
        PosteSociete.Poste poste = PosteSociete.construire(ctx.getSource().getServer().overworld(), pos);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.poste", poste.porte().toShortString()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Construit une cellule du Rising : entrée cachée (moitié basse) à la position donnée, salle dans la direction donnée. */
    private static int construireCellule(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, ctx.getSource().getServer().overworld(), "pos");
        Direction direction = lireEnum(ctx, "direction", HORIZONTALES);
        CelluleRising.Cellule cellule = CelluleRising.construire(ctx.getSource().getServer().overworld(), pos, direction);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.cellule", cellule.entree().toShortString(), direction.getSerializedName()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Oublie le poste ou la cellule qui contient cette position (les blocs restent). */
    private static int oublierStructure(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, ctx.getSource().getServer().overworld(), "pos");
        MinecraftServer server = ctx.getSource().getServer();
        // Pas de court-circuit : un poste et une cellule collés sont oubliés ensemble
        if (!(PosteSociete.oublier(server, pos) | CelluleRising.oublier(server, pos))) {
            throw PAS_DE_STRUCTURE.create(pos.toShortString());
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.oublier", pos.toShortString()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Fait tomber la cellule qui contient cette position, comme une dénonciation (sans effet sur les jauges). */
    private static int faireTomber(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, ctx.getSource().getServer().overworld(), "pos");
        CelluleRising.Cellule cellule = CelluleRising.contenant(ctx.getSource().getServer(), pos);
        if (cellule == null) {
            throw PAS_DE_STRUCTURE.create(pos.toShortString());
        }
        CelluleRising.tomber(ctx.getSource().getServer().overworld(), cellule, "commande");
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.tomber", cellule.entree().toShortString()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Dénonciation (G5.10) ---

    private static int lireDenonciation(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        String connues = CelluleRising.connues(joueur).stream().map(cellule -> cellule.entree().toShortString()).collect(Collectors.joining(" ; "));
        Long quarantaine = joueur.getAttached(CelluleRising.QUARANTAINE);
        Long blanchi = joueur.getAttached(ActionsFaction.DERNIER_BLANCHIMENT);
        Component fin = CelluleRising.enQuarantaine(joueur) ? Component.literal(String.valueOf(quarantaine)) : Component.translatable("commande.redrising.aucun");
        Component jour = blanchi == null ? Component.translatable("commande.redrising.aucun") : Component.literal(String.valueOf(blanchi));
        int peines = CelluleRising.peinesEnAttente(ctx.getSource().getServer(), joueur.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.denonciation.get", joueur.getDisplayName(),
                connues.isEmpty() ? Component.translatable("commande.redrising.aucun") : Component.literal(connues), fin, jour, peines), false);
        return CelluleRising.connues(joueur).size();
    }

    /** Oublie les cellules connues, la quarantaine et le dernier jour de blanchiment. */
    private static int effacerDenonciation(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        joueur.removeAttached(CelluleRising.CONNUES);
        joueur.removeAttached(CelluleRising.QUARANTAINE);
        joueur.removeAttached(ActionsFaction.DERNIER_BLANCHIMENT);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.denonciation.reset", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Patrouilles et contrôles (G5.5) ---

    private static RequiredArgumentBuilder<CommandSourceStack, ?> argPoint(int numero) {
        return Commands.argument("point" + numero, BlockPosArgument.blockPos());
    }

    /** Un PNJ Gris au premier point, qui fait la ronde entre les points donnés. */
    private static int creerPatrouille(CommandContext<CommandSourceStack> ctx, int nombre) throws CommandSyntaxException {
        List<BlockPos> points = new ArrayList<>();
        for (int i = 1; i <= nombre; i++) {
            points.add(BlockPosArgument.getLoadedBlockPos(ctx, ctx.getSource().getLevel(), "point" + i));
        }
        Villager gris = PatrouillePnj.creer(ctx.getSource().getLevel(), points);
        if (gris == null) {
            throw PNJ_REFUSE.create();
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.patrouille.creer", gris.getUUID().toString(), nombre), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int lirePatrouille(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        Ronde ronde = pnj.getAttached(PatrouillePnj.RONDE);
        if (ronde == null) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.patrouille.aucune", pnj.getDisplayName()), false);
            return 0;
        }
        String points = ronde.points().stream().map(BlockPos::toShortString).collect(Collectors.joining(" ; "));
        UUID suspect = PatrouillePnj.suspectControle(pnj);
        Component controle = suspect == null ? Component.translatable("commande.redrising.aucun") : Component.literal(suspect.toString());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.patrouille.get", pnj.getDisplayName(), points,
                ronde.index() + 1, controle, PatrouillePnj.fouilleRestante(pnj)), false);
        return ronde.index() + 1;
    }

    private static int retirerPatrouille(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        PatrouillePnj.retirerRonde(pnj);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.patrouille.clear", pnj.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int lireControle(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        PatrouillePnj.SuiviControle suivi = PatrouillePnj.suivi(joueur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.controle.get", joueur.getDisplayName(),
                suivi.dernierControle(), suivi.dernierJourBlanchi()), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Efface l'attente entre deux contrôles et le dernier jour blanchi. */
    private static int effacerControle(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        joueur.removeAttached(PatrouillePnj.SUIVI_CONTROLE);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.controle.reset", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Traque (G5.6) ---

    private static int lireTraque(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        TraqueGris.Interrogatoire interrogatoire = joueur.getAttached(TraqueGris.INTERROGATOIRE);
        Traque traque = joueur.getAttached(TraqueGris.TRAQUE);
        Component message;
        if (interrogatoire != null) {
            message = Component.translatable("commande.redrising.traque.interrogatoire", joueur.getDisplayName(), interrogatoire.restant() / 20);
        } else if (traque != null) {
            message = Component.translatable("commande.redrising.traque.en_cours", joueur.getDisplayName(), traque.duree() / 20,
                    traque.horsDeVue() / 20, TraqueGris.nombreDeChasseurs(joueur), traque.tues());
        } else {
            message = Component.translatable("commande.redrising.traque.aucune", joueur.getDisplayName());
        }
        ctx.getSource().sendSuccess(() -> message, false);
        return traque != null || interrogatoire != null ? 1 : 0;
    }

    /** Suspicion au maximum, et la traque commence tout de suite. */
    private static int lancerTraque(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        TraqueGris.annuler(joueur);
        JaugesJoueur.set(joueur, Jauge.SUSPICION, Acces.SEUIL_TRAQUE);
        TraqueGris.commencer(joueur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.traque.start", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Fin de la traque (ou de l'interrogatoire), Suspicion ramenée à celle d'un retrait des Gris pour qu'elle ne reparte pas. */
    private static int arreterTraque(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        TraqueGris.annuler(joueur);
        JaugesJoueur.set(joueur, Jauge.SUSPICION, Traque.SUSPICION_RETRAIT);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.traque.stop", joueur.getDisplayName(), Traque.SUSPICION_RETRAIT), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int arreterJoueur(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        TraqueGris.arreter(joueur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.traque.arreter", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Boutiques (G5.9) ---

    private static int lireBoutique(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        Boutique boutique = pnj.getAttached(Boutiques.BOUTIQUE);
        Component nom = boutique == null ? Component.translatable("commande.redrising.aucun") : Component.literal(boutique.getSerializedName());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.boutique.get", pnj.getDisplayName(), nom), false);
        return boutique == null ? 0 : 1;
    }

    /** Marque un PNJ comme tenant une boutique (mondes construits avant G5.9, tests). */
    private static int changerBoutique(CommandContext<CommandSourceStack> ctx, @Nullable Boutique boutique) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        if (boutique == null) {
            pnj.removeAttached(Boutiques.BOUTIQUE);
        } else {
            pnj.setAttached(Boutiques.BOUTIQUE, boutique);
        }
        Component nom = boutique == null ? Component.translatable("commande.redrising.aucun") : Component.literal(boutique.getSerializedName());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.boutique.set", pnj.getDisplayName(), nom), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int lireAchats(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        AchatsDuJour achats = Boutiques.achats(joueur);
        String liste = new TreeMap<>(achats.nombres()).entrySet().stream()
                .map(entree -> entree.getKey() + " ×" + entree.getValue()).collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.boutique.achats.get", joueur.getDisplayName(), achats.jour(), liste), false);
        return achats.nombres().size();
    }

    private static int effacerAchats(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        joueur.removeAttached(Boutiques.ACHATS);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.boutique.achats.reset", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Objets rebelles (G5.8) ---

    private static int listerFumees(CommandContext<CommandSourceStack> ctx) {
        ServerLevel monde = ctx.getSource().getLevel();
        List<Nuage> nuages = Vue.nuages(monde);
        String liste = nuages.stream()
                .map(nuage -> BlockPos.containing(nuage.centre()).toShortString() + " (" + Math.max(0, nuage.fin() - monde.getGameTime()) / 20 + " s)")
                .collect(Collectors.joining(" ; "));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.fumee.list", nuages.size(), liste), false);
        return nuages.size();
    }

    private static int enfumer(CommandContext<CommandSourceStack> ctx) {
        Vec3 centre = Vec3Argument.getVec3(ctx, "pos");
        Vue.enfumer(ctx.getSource().getLevel(), centre);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.fumee.creee", BlockPos.containing(centre).toShortString()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int dissiperFumees(CommandContext<CommandSourceStack> ctx) {
        int nombre = Vue.dissiper(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.fumee.clear", nombre), true);
        return nombre;
    }

    /** Chausse-trappes d'un joueur, ou toutes sans joueur. */
    private static int listerChausseTrappes(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer joueur) {
        RegistreChausseTrappes registre = ChausseTrappes.registre(ctx.getSource().getServer());
        List<RegistreChausseTrappes.Piege> pieges = joueur == null ? registre.pieges() : registre.de(joueur.getUUID());
        String liste = pieges.stream().map(piege -> piege.pos().toShortString()).collect(Collectors.joining(" ; "));
        Component qui = joueur == null ? Component.translatable("commande.redrising.tous") : joueur.getDisplayName();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.chausse_trappes.list", qui, pieges.size(), liste), false);
        return pieges.size();
    }

    private static int effacerChausseTrappes(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer joueur) {
        int nombre = ChausseTrappes.effacer(ctx.getSource().getServer(), joueur == null ? null : joueur.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.chausse_trappes.clear", nombre), true);
        return nombre;
    }

    private static int lireContrebande(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        Long dernier = joueur.getAttached(Contrebande.DERNIER_REPERAGE);
        Component quand = dernier == null ? Component.translatable("commande.redrising.aucun") : Component.literal(String.valueOf(dernier));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.contrebande.get", joueur.getDisplayName(), quand), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int effacerContrebande(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        joueur.removeAttached(Contrebande.DERNIER_REPERAGE);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.contrebande.reset", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- Coffres d'un camp (G5.2) ---

    private static int lireCoffre(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockEntity coffre = coffre(ctx);
        Camp camp = coffre.getAttached(ActionsFaction.CAMP);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.coffre.get", coffre.getBlockPos().toShortString(), nomCamp(camp)), false);
        return camp == null ? 0 : camp.ordinal() + 1;
    }

    /** {@code null} : le coffre n'appartient plus à aucun camp. */
    private static int changerCoffre(CommandContext<CommandSourceStack> ctx, @Nullable Camp camp) throws CommandSyntaxException {
        BlockEntity coffre = coffre(ctx);
        if (camp == null) {
            coffre.removeAttached(ActionsFaction.CAMP);
        } else {
            coffre.setAttached(ActionsFaction.CAMP, camp);
        }
        coffre.setChanged(); // à sauvegarder avec le bloc
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.coffre.set", coffre.getBlockPos().toShortString(), nomCamp(camp)), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Coffre ou tonneau à la position donnée (ce qui s'ouvre comme un coffre simple). */
    private static BlockEntity coffre(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockEntity bloc = ctx.getSource().getLevel().getBlockEntity(pos);
        if (bloc instanceof ChestBlockEntity || bloc instanceof BarrelBlockEntity) {
            return bloc;
        }
        throw PAS_UN_COFFRE.create(pos.toShortString());
    }

    private static Component nomCamp(@Nullable Camp camp) {
        return camp == null ? Component.translatable("commande.redrising.aucun") : nom("camp", camp);
    }

    // --- Personnages canon (G6.4) ---

    private static int incarnerPnj(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        String personnage = StringArgumentType.getString(ctx, "personnage");
        Set<UUID> ancien = new HashSet<>();
        switch (PnjEssentiels.incarner(pnj, personnage, ancien)) {
            case INCONNU -> throw PERSONNAGE_INCONNU.create(personnage);
            case MORT -> throw PERSONNAGE_MORT.create(personnage);
            case DONNE -> ctx.getSource().sendSuccess(() -> ancien.isEmpty()
                    ? Component.translatable("commande.redrising.personnage.donne", personnage, pnj.getStringUUID())
                    : Component.translatable("commande.redrising.personnage.deplace", personnage, pnj.getStringUUID(), ancien.iterator().next().toString()), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int libererPnj(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        PnjEssentiels.liberer(pnj);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.libere", pnj.getStringUUID()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int mettreKo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        PnjEssentiels.mettreATerre(pnj);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.ko", pnj.getStringUUID()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int reveillerPnj(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        PnjEssentiels.reveiller(pnj);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.reveille", pnj.getStringUUID()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Chaque personnage déclaré : son jalon, essentiel ou pas, mort ou pas, et le PNJ qui l'incarne. */
    private static int listerPersonnages(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        EtatMonde etat = EtatMondeServeur.get(server);
        Map<String, UUID> porteurs = PnjEssentiels.porteurs(server);
        for (Personnage personnage : Personnages.tous()) {
            MortPersonnage mort = etat.morts().get(personnage.id());
            Long fuite = etat.enFuite().get(personnage.id());
            Component statut = mort != null ? Component.translatable("commande.redrising.personnage.mort", temps(mort.temps()))
                    : fuite != null ? Component.translatable("commande.redrising.personnage.en_fuite", temps(fuite))
                    : Component.translatable(Personnages.essentiel(personnage, etat) ? "commande.redrising.personnage.essentiel" : "commande.redrising.personnage.tuable");
            UUID porteur = porteurs.get(personnage.id());
            ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.ligne", personnage.id(),
                    personnage.jalon() == null ? Component.translatable("commande.redrising.aucun") : Component.literal(personnage.jalon()), statut,
                    porteur == null ? Component.translatable("commande.redrising.aucun") : Component.literal(porteur.toString()),
                    String.format(Locale.ROOT, "%.1f", etat.attitudeMoyenne(personnage.id()))), false);
        }
        if (Personnages.tous().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.aucun"), false);
        }
        return Personnages.tous().size();
    }

    private static int tuerPersonnage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Villager pnj = villageois(ctx);
        PnjEssentiels.tuer(pnj, ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.tue", pnj.getStringUUID()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int oublierPersonnage(CommandContext<CommandSourceStack> ctx) {
        String personnage = StringArgumentType.getString(ctx, "personnage");
        modifierEtat(ctx, etat -> etat.sansMort(personnage));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.oublie", personnage), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Argument : un personnage déclaré, avec l'autocomplétion. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> argPersonnageDeclare() {
        return Commands.argument("personnage", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Personnages.tous().stream().map(Personnage::id), builder));
    }

    private static String personnageDeclare(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String personnage = StringArgumentType.getString(ctx, "personnage");
        if (Personnages.get(personnage) == null) {
            throw PERSONNAGE_INCONNU.create(personnage);
        }
        return personnage;
    }

    /** L'attitude d'un personnage envers ce joueur, ses gains du jour, et sa moyenne pour le monde. */
    private static int lireAttitude(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) throws CommandSyntaxException {
        String personnage = personnageDeclare(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        EtatMonde etat = EtatMondeServeur.get(server);
        Attitude attitude = etat.attitude(personnage, joueur.getUUID());
        long jour = server.overworld().getGameTime() / ActionsFaction.TICKS_PAR_JOUR;
        int gains = attitude.jour() == jour ? attitude.gainsDuJour() : 0;
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.attitude", personnage, joueur.getDisplayName(),
                attitude.valeur(), gains, Attitude.PLAFOND_GAINS_PAR_JOUR, String.format(Locale.ROOT, "%.1f", etat.attitudeMoyenne(personnage))), false);
        return attitude.valeur();
    }

    /** Debug : fixe ou ajoute, borné, sans plafond du jour (ni message au joueur). */
    private static int changerAttitude(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur, boolean ajout) throws CommandSyntaxException {
        String personnage = personnageDeclare(ctx);
        int saisie = IntegerArgumentType.getInteger(ctx, "valeur");
        Attitude avant = EtatMondeServeur.get(ctx.getSource().getServer()).attitude(personnage, joueur.getUUID());
        Attitude apres = avant.fixer(ajout ? avant.valeur() + saisie : saisie);
        modifierEtat(ctx, etat -> etat.avecAttitude(personnage, joueur.getUUID(), apres));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.personnage.attitude.set", personnage, joueur.getDisplayName(), apres.valeur()), true);
        return apres.valeur();
    }

    private static Villager villageois(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity entite = EntityArgument.getEntity(ctx, "pnj");
        if (entite instanceof Villager pnj) {
            return pnj;
        }
        throw PAS_UN_VILLAGEOIS.create(entite.getDisplayName());
    }

    private static Component metier(Villager pnj) {
        return pnj.getVillagerData().profession().value().name();
    }

    // --- Jauges ---

    private static int lireJauges(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        Jauges jauges = JaugesJoueur.get(joueur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.jauge.get",
                joueur.getDisplayName(), jauges.loyaute(), jauges.soutien(), jauges.suspicion()), false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code ajout} : la valeur saisie s'ajoute à la jauge au lieu de la remplacer. */
    private static int changerJauge(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur, boolean ajout) throws CommandSyntaxException {
        Jauge jauge = lireEnum(ctx, "jauge", Jauge.values());
        int saisie = IntegerArgumentType.getInteger(ctx, "valeur");
        long demandee = ajout ? (long) JaugesJoueur.get(joueur, jauge) + saisie : saisie;
        int obtenue = JaugesJoueur.set(joueur, jauge, demandee);
        Component nom = Component.translatable(jauge.getTranslationKey());
        ctx.getSource().sendSuccess(() -> obtenue == demandee
                ? Component.translatable("commande.redrising.jauge.set", nom, joueur.getDisplayName(), obtenue)
                : Component.translatable("commande.redrising.jauge.bornee", nom, joueur.getDisplayName(), obtenue, demandee), true);
        return obtenue;
    }

    // --- Jalons ---

    private static int listerJalons(CommandContext<CommandSourceStack> ctx) {
        EtatMonde etat = EtatMondeServeur.get(ctx.getSource().getServer());
        Map<String, Issue> jalons = etat.jalons();
        Component enAttente = etat.effetsEnAttente().isEmpty() ? Component.translatable("commande.redrising.aucun")
                : Component.literal(String.join(", ", new TreeSet<>(etat.effetsEnAttente())));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.jalon.list", liste(jalons, "issue"), enAttente), false);
        return jalons.size();
    }

    /** Argument : un jalon déclaré, avec l'autocomplétion. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> argJalonDeclare() {
        return Commands.argument("jalon", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Jalons.tous().stream().map(Jalon::id), builder));
    }

    /** Chaque jalon déclaré pas encore passé : en route (dans combien de temps, pourquoi, avec quelle issue) ou pas encore. */
    private static int listerProchains(CommandContext<CommandSourceStack> ctx) {
        EtatMonde etat = EtatMondeServeur.get(ctx.getSource().getServer());
        int nombre = 0;
        for (Jalon jalon : Jalons.tous()) {
            if (etat.jalons().containsKey(jalon.id())) {
                continue;
            }
            nombre++;
            Component ligne;
            if (etat.enRoute().containsKey(jalon.id())) {
                Bilan bilan = Jalons.bilan(jalon, etat);
                Echeance echeance = jalon.echeance();
                ligne = Component.translatable("commande.redrising.jalon.prochain", jalon.id(),
                        temps(Math.max(0, Jalons.restant(jalon, etat))), temps(Jalons.delai(jalon, etat)),
                        temps(echeance.base()), String.format(Locale.ROOT, "%.2f", bilan.rythme()), temps(echeance.minimum()), temps(echeance.maximum()),
                        nom("issue", jalon.issue(bilan)));
            } else {
                ligne = Component.translatable("commande.redrising.jalon.pas_en_route", jalon.id(), nom("epoque", jalon.epoque()));
            }
            ctx.getSource().sendSuccess(() -> ligne, false);
        }
        if (nombre == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.jalon.aucun_prochain"), false);
        }
        return nombre;
    }

    /** Passage complet (issue, époque, sauvegarde, événement), avec l'issue donnée ou celle que donne l'état du monde. */
    private static int declencherJalon(CommandContext<CommandSourceStack> ctx, @Nullable Issue forcee) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "jalon");
        Jalon jalon = Jalons.get(id);
        if (jalon == null) {
            throw JALON_INCONNU.create(id);
        }
        MinecraftServer server = ctx.getSource().getServer();
        EtatMonde etat = EtatMondeServeur.get(server);
        if (etat.jalons().containsKey(id)) {
            throw JALON_DEJA_PASSE.create(id);
        }
        Issue issue = forcee != null ? forcee : jalon.issue(Jalons.bilan(jalon, etat));
        MoteurJalons.passer(server, jalon, issue, JalonsEvents.Raison.COMMANDE);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.jalon.declenche", id, nom("issue", issue)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int marquerEffets(CommandContext<CommandSourceStack> ctx, boolean enAttente) throws CommandSyntaxException {
        String jalon = StringArgumentType.getString(ctx, "jalon");
        if (!EtatMondeServeur.get(ctx.getSource().getServer()).jalons().containsKey(jalon)) {
            throw JALON_ABSENT.create(jalon);
        }
        modifierEtat(ctx, etat -> etat.avecEffetsEnAttente(jalon, enAttente));
        ctx.getSource().sendSuccess(() -> Component.translatable(enAttente ? "commande.redrising.jalon.effets.en_attente"
                : "commande.redrising.jalon.effets.appliques", jalon), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int fixerJalon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String jalon = StringArgumentType.getString(ctx, "jalon");
        Issue issue = lireEnum(ctx, "issue", Issue.values());
        modifierEtat(ctx, etat -> etat.avecIssue(jalon, issue));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.jalon.set", jalon, nom("issue", issue)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int effacerJalon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String jalon = StringArgumentType.getString(ctx, "jalon");
        if (!EtatMondeServeur.get(ctx.getSource().getServer()).jalons().containsKey(jalon)) {
            throw JALON_ABSENT.create(jalon);
        }
        modifierEtat(ctx, etat -> etat.sansIssue(jalon));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.jalon.clear", jalon), true);
        return Command.SINGLE_SUCCESS;
    }

    // --- État du monde ---

    private static int lireEtat(CommandContext<CommandSourceStack> ctx) {
        EtatMonde etat = EtatMondeServeur.get(ctx.getSource().getServer());
        ModeNaissance mode = Naissance.mode(ctx.getSource().getServer());
        Horloge horloge = etat.horloge();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat",
                etat.schemaVersion(),
                mode.getDisplayName(),
                nom("epoque", etat.epoque()),
                liste(etat.jalons(), "issue"),
                liste(etat.zones(), "camp"),
                temps(horloge.temps()), temps(horloge.presence()),
                horloge.aide(), horloge.tort(), horloge.net(), effets(etat)), false);
        return etat.schemaVersion();
    }

    // --- Effets des jalons (G6.3) ---

    /** « id : levier niveau N (jalon), encore X » pour chaque effet en cours, ou « aucun ». */
    private static Component effets(EtatMonde etat) {
        if (etat.effets().isEmpty()) {
            return Component.translatable("commande.redrising.aucun");
        }
        MutableComponent resultat = Component.empty();
        boolean premier = true;
        for (Map.Entry<String, EffetEnCours> entree : new TreeMap<>(etat.effets()).entrySet()) {
            EffetEnCours effet = entree.getValue();
            Component duree = effet.fin() == EffetEnCours.PERMANENT ? Component.translatable("commande.redrising.effet.permanent")
                    : Component.translatable("commande.redrising.effet.reste", temps(Math.max(0, effet.fin() - etat.horloge().temps())));
            if (!premier) {
                resultat.append(" ; ");
            }
            resultat.append(Component.translatable("commande.redrising.effet.ligne", entree.getKey(), nom("levier", effet.levier()),
                    effet.niveau(), effet.jalon(), duree));
            premier = false;
        }
        return resultat;
    }

    private static int listerEffets(CommandContext<CommandSourceStack> ctx) {
        EtatMonde etat = EtatMondeServeur.get(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.effet.list", effets(etat)), false);
        return etat.effets().size();
    }

    /** Pose un levier à la main (identifiant {@code debug/<levier>}, remplacé s'il existe), permanent ou pour un temps de jeu. */
    private static int poserEffet(CommandContext<CommandSourceStack> ctx, long duree) throws CommandSyntaxException {
        Levier levier = lireEnum(ctx, "levier", Levier.values());
        int niveau = IntegerArgumentType.getInteger(ctx, "niveau");
        MinecraftServer server = ctx.getSource().getServer();
        long temps = HorlogeMonde.get(server).temps();
        String id = "debug/" + levier.getSerializedName();
        EffetEnCours effet = new EffetEnCours(levier, niveau, "debug", temps, duree == EffetEnCours.PERMANENT ? EffetEnCours.PERMANENT : temps + duree);
        modifierEtat(ctx, etat -> etat.avecEffet(id, effet));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.effet.pose", id, nom("levier", levier), effet.niveau()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int retirerEffet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        if (!EtatMondeServeur.get(ctx.getSource().getServer()).effets().containsKey(id)) {
            throw EFFET_ABSENT.create(id);
        }
        modifierEtat(ctx, etat -> etat.sansEffet(id));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.effet.retire", id), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int lireRecus(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        Set<String> recus = joueur.getAttachedOrElse(EffetsMonde.RECUS, Set.of());
        Long arrivee = joueur.getAttached(EffetsMonde.ARRIVEE);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.effet.recus.get", joueur.getDisplayName(),
                arrivee == null ? Component.translatable("commande.redrising.aucun") : temps(arrivee),
                recus.isEmpty() ? Component.translatable("commande.redrising.aucun") : Component.literal(String.join(", ", new TreeSet<>(recus)))), false);
        return recus.size();
    }

    /** Oublie ce que le joueur a reçu : les versements dont il a l'âge lui seront de nouveau livrés. */
    private static int effacerRecus(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        joueur.removeAttached(EffetsMonde.RECUS);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.effet.recus.reset", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Temps de jeu en jours et heures de jeu (24 000 et 1 000 ticks), suivi du nombre de ticks. */
    private static Component temps(long ticks) {
        return Component.translatable("commande.redrising.etat.temps.duree",
                ticks / ActionsFaction.TICKS_PAR_JOUR, ticks % ActionsFaction.TICKS_PAR_JOUR / 1000, ticks);
    }

    private static int changerTemps(CommandContext<CommandSourceStack> ctx, boolean ajouter) {
        long ticks = LongArgumentType.getLong(ctx, "ticks");
        MinecraftServer server = ctx.getSource().getServer();
        HorlogeMonde.modifier(server, horloge -> horloge.avecTemps(ajouter ? horloge.temps() + ticks : ticks));
        long obtenu = HorlogeMonde.get(server).temps();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.temps", temps(obtenu)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int changerPresence(CommandContext<CommandSourceStack> ctx, boolean ajouter) {
        long ticks = LongArgumentType.getLong(ctx, "ticks");
        MinecraftServer server = ctx.getSource().getServer();
        HorlogeMonde.modifier(server, horloge -> horloge.avecPresence(ajouter ? horloge.presence() + ticks : ticks));
        long obtenue = HorlogeMonde.get(server).presence();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.presence", temps(obtenue)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int changerSoutienCumule(CommandContext<CommandSourceStack> ctx, String compteur, boolean ajouter) {
        long valeur = LongArgumentType.getLong(ctx, "valeur");
        MinecraftServer server = ctx.getSource().getServer();
        boolean aide = compteur.equals("aide");
        HorlogeMonde.modifier(server, horloge -> {
            long avant = aide ? horloge.aide() : horloge.tort();
            long apres = ajouter ? avant + valeur : valeur;
            return aide ? horloge.avecAide(apres) : horloge.avecTort(apres);
        });
        Horloge horloge = HorlogeMonde.get(server);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.soutien", horloge.aide(), horloge.tort(), horloge.net()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int lireContribution(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        long jour = HorlogeMonde.jour(joueur);
        ContributionDuJour contribution = HorlogeMonde.contribution(joueur);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.contribution.get", joueur.getDisplayName(), jour,
                contribution.aide(jour), contribution.tort(jour), ContributionDuJour.PLAFOND), false);
        return contribution.aide(jour) + contribution.tort(jour);
    }

    private static int effacerContribution(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur) {
        joueur.removeAttached(HorlogeMonde.CONTRIBUTION);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.contribution.reset", joueur.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int changerModeNaissance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ModeNaissance mode = lireEnum(ctx, "mode", ModeNaissance.values());
        Naissance.changerMode(ctx.getSource().getServer(), mode);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.naissance", mode.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int changerEpoque(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Epoque epoque = lireEnum(ctx, "epoque", Epoque.values());
        modifierEtat(ctx, etat -> etat.avecEpoque(epoque));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.epoque", nom("epoque", epoque)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int fixerZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String zone = StringArgumentType.getString(ctx, "zone");
        Camp camp = lireEnum(ctx, "camp", Camp.values());
        modifierEtat(ctx, etat -> etat.avecZone(zone, camp));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.zone.set", zone, nom("camp", camp)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int effacerZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String zone = StringArgumentType.getString(ctx, "zone");
        if (!EtatMondeServeur.get(ctx.getSource().getServer()).zones().containsKey(zone)) {
            throw ZONE_ABSENTE.create(zone);
        }
        modifierEtat(ctx, etat -> etat.sansZone(zone));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat.zone.clear", zone), true);
        return Command.SINGLE_SUCCESS;
    }

    private static void modifierEtat(CommandContext<CommandSourceStack> ctx, Function<EtatMonde, EtatMonde> modification) {
        MinecraftServer server = ctx.getSource().getServer();
        EtatMondeServeur.set(server, modification.apply(EtatMondeServeur.get(server)));
    }

    // --- Outils ---

    /** Argument {@code [joueur]} optionnel en fin de commande. */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> argJoueur(ActionJoueur action) {
        return Commands.argument("joueur", EntityArgument.player())
                .executes(ctx -> action.executer(ctx, EntityArgument.getPlayer(ctx, "joueur")));
    }

    /** Argument texte limité aux identifiants d'un enum, avec l'autocomplétion. */
    private static <E extends Enum<E> & StringRepresentable> RequiredArgumentBuilder<CommandSourceStack, String> argEnum(String nom, E[] valeurs) {
        return Commands.argument(nom, StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(valeurs).map(StringRepresentable::getSerializedName), builder));
    }

    private static <E extends Enum<E> & StringRepresentable> E lireEnum(CommandContext<CommandSourceStack> ctx, String nom, E[] valeurs) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, nom);
        for (E valeur : valeurs) {
            if (valeur.getSerializedName().equals(id)) {
                return valeur;
            }
        }
        throw VALEUR_INCONNUE.create(id);
    }

    /** Nom traduit d'une valeur d'enum : {@code <type>.redrising.<id>}, ex. {@code issue.redrising.canon}. */
    private static Component nom(String type, StringRepresentable valeur) {
        return Component.translatable(type + ".redrising." + valeur.getSerializedName());
    }

    /** « id : valeur, id : valeur » trié par identifiant, ou « aucun ». */
    private static <V extends StringRepresentable> Component liste(Map<String, V> entrees, String type) {
        if (entrees.isEmpty()) {
            return Component.translatable("commande.redrising.aucun");
        }
        MutableComponent resultat = Component.empty();
        boolean premier = true;
        for (Map.Entry<String, V> entree : new TreeMap<>(entrees).entrySet()) {
            if (!premier) {
                resultat.append(", ");
            }
            resultat.append(entree.getKey() + " : ").append(nom(type, entree.getValue()));
            premier = false;
        }
        return resultat;
    }
}
