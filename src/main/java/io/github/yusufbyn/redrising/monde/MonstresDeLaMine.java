package io.github.yusufbyn.redrising.monde;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les monstres de la mine (G7.2.3, GDD section 7) : la règle des apparitions naturelles dans le vide de la mine, appelée par
 * {@code NaturalSpawnerMixin} juste avant qu'un monstre tiré par Minecraft soit ajouté au monde (il est déjà à sa place).
 *
 * <ul>
 *     <li>Ni creeper (il ouvrirait les murs, et les cellules) ni slime (il apparaît sans condition de lumière dans un slime
 *     chunk sous y = 40).</li>
 *     <li>🟡 {@value #PLAFOND} monstres par joueur, comptés dans le vide de la mine à {@value #RAYON} blocs ou moins de lui (le
 *     joueur le plus proche du monstre) : au-delà, l'apparition est refusée.</li>
 *     <li>Le réseau éclairé n'a besoin d'aucune règle : Minecraft ne fait apparaître un monstre qu'à une lumière de bloc de 0.</li>
 * </ul>
 *
 * <p>Tout tourne sur le fil du serveur. Hors de l'emprise de la mine, la règle ne coûte que quelques comparaisons.</p>
 */
public final class MonstresDeLaMine {
    /** 🟡 Plafond par joueur, et distance de comptage. */
    public static final int PLAFOND = 16;
    public static final int RAYON = 128;
    /** Positions des dernières apparitions acceptées dans la mine (debug, tests). */
    private static final int APPARITIONS_GARDEES = 1024;

    private record Compte(long tick, int nombre) {
    }

    private static final Map<UUID, Compte> COMPTES = new HashMap<>();
    private static final Deque<BlockPos> APPARITIONS = new ArrayDeque<>();

    private MonstresDeLaMine() {
    }

    /** L'apparition naturelle de ce monstre est-elle refusée ? */
    public static boolean refusee(ServerLevel niveau, Mob mob) {
        BlockPos centre = MineLykos.centre();
        if (centre == null || !(mob instanceof Enemy) || !niveau.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        BlockPos pos = mob.blockPosition();
        int r = PlanMars.EMPRISE_MINE * 16 + 16;
        if (Math.abs(pos.getX() - centre.getX()) > r || Math.abs(pos.getZ() - centre.getZ()) > r) {
            return false;
        }
        PlanDeMine plan = MineLykos.planEnJeu(niveau);
        if (plan == null || !plan.interieur(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        if (mob.getType() == EntityTypes.CREEPER || mob.getType() == EntityTypes.SLIME) {
            return true;
        }
        Player joueur = niveau.getNearestPlayer(mob, RAYON);
        if (joueur != null) {
            long tick = niveau.getGameTime();
            Compte compte = COMPTES.get(joueur.getUUID());
            int nombre = compte != null && compte.tick() == tick ? compte.nombre() : compter(niveau, plan, joueur);
            if (nombre >= PLAFOND) {
                COMPTES.put(joueur.getUUID(), new Compte(tick, nombre));
                return true;
            }
            // Accepté : il compte déjà pour les essais suivants du même tick
            COMPTES.put(joueur.getUUID(), new Compte(tick, nombre + 1));
        }
        APPARITIONS.addLast(pos);
        if (APPARITIONS.size() > APPARITIONS_GARDEES) {
            APPARITIONS.removeFirst();
        }
        return false;
    }

    /** Les monstres dans le vide de la mine à {@value #RAYON} blocs ou moins de ce joueur. */
    public static int compter(ServerLevel niveau, PlanDeMine plan, Player joueur) {
        List<Mob> monstres = niveau.getEntitiesOfClass(Mob.class, new AABB(joueur.blockPosition()).inflate(RAYON),
                mob -> mob instanceof Enemy && mob.distanceTo(joueur) <= RAYON);
        int nombre = 0;
        for (Mob mob : monstres) {
            BlockPos pos = mob.blockPosition();
            if (plan.interieur(pos.getX(), pos.getY(), pos.getZ())) {
                nombre++;
            }
        }
        return nombre;
    }

    /** Les positions des dernières apparitions acceptées dans la mine (debug, tests). */
    public static List<BlockPos> apparitions() {
        return List.copyOf(APPARITIONS);
    }

    public static void remettre() {
        APPARITIONS.clear();
        COMPTES.clear();
    }
}
