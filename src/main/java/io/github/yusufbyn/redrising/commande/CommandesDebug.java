package io.github.yusufbyn.redrising.commande;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import io.github.yusufbyn.redrising.factions.ActionsFaction;
import io.github.yusufbyn.redrising.factions.Camp;
import io.github.yusufbyn.redrising.factions.Jauge;
import io.github.yusufbyn.redrising.factions.Jauges;
import io.github.yusufbyn.redrising.factions.JaugesJoueur;
import io.github.yusufbyn.redrising.jalons.Epoque;
import io.github.yusufbyn.redrising.jalons.Issue;
import io.github.yusufbyn.redrising.monde.PosteSociete;
import io.github.yusufbyn.redrising.pnj.PnjCouleur;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    private static final DynamicCommandExceptionType PAS_UN_VILLAGEOIS = new DynamicCommandExceptionType(
            entite -> Component.translatable("commande.redrising.pnj.pas_villageois", entite));
    private static final DynamicCommandExceptionType PAS_UN_COFFRE = new DynamicCommandExceptionType(
            pos -> Component.translatable("commande.redrising.coffre.absent", pos));
    private static final DynamicCommandExceptionType PAS_DE_POSTE = new DynamicCommandExceptionType(
            pos -> Component.translatable("commande.redrising.structure.absente", pos));
    private static final SimpleCommandExceptionType PNJ_REFUSE = new SimpleCommandExceptionType(
            Component.translatable("commande.redrising.pnj.refuse"));
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
                                        .then(Commands.literal("aucun").executes(ctx -> changerCampPnj(ctx, null))))))
                .then(Commands.literal("structure")
                        .then(Commands.literal("list").executes(CommandesDebug::listerPostes))
                        .then(Commands.literal("poste")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CommandesDebug::construirePoste)))
                        .then(Commands.literal("oublier")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CommandesDebug::oublierPoste))))
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
                                .then(Commands.argument("jalon", StringArgumentType.word()).executes(CommandesDebug::effacerJalon))))
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
                                        .then(Commands.argument("zone", StringArgumentType.word()).executes(CommandesDebug::effacerZone))))));
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

    // --- Structures (G5.3) ---

    private static int listerPostes(CommandContext<CommandSourceStack> ctx) {
        List<PosteSociete.Poste> postes = PosteSociete.postes(ctx.getSource().getServer());
        String liste = postes.stream().map(poste -> poste.porte().toShortString()).collect(Collectors.joining(" ; "));
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.list", postes.size(),
                Acces.SEUIL_LOYAUTE_SOCIETE, postes.isEmpty() ? Component.translatable("commande.redrising.aucun") : Component.literal(liste)), false);
        return postes.size();
    }

    /** Construit un poste de la Société, coin nord-ouest du sol à la position donnée (la porte est au nord). */
    private static int construirePoste(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, ctx.getSource().getServer().overworld(), "pos");
        PosteSociete.Poste poste = PosteSociete.construire(ctx.getSource().getServer().overworld(), pos);
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.poste", poste.porte().toShortString()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int oublierPoste(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, ctx.getSource().getServer().overworld(), "pos");
        if (!PosteSociete.oublier(ctx.getSource().getServer(), pos)) {
            throw PAS_DE_POSTE.create(pos.toShortString());
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.structure.oublier", pos.toShortString()), true);
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
        Map<String, Issue> jalons = EtatMondeServeur.get(ctx.getSource().getServer()).jalons();
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.jalon.list", liste(jalons, "issue")), false);
        return jalons.size();
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
        ctx.getSource().sendSuccess(() -> Component.translatable("commande.redrising.etat",
                etat.schemaVersion(),
                mode.getDisplayName(),
                nom("epoque", etat.epoque()),
                liste(etat.jalons(), "issue"),
                liste(etat.zones(), "camp")), false);
        return etat.schemaVersion();
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
