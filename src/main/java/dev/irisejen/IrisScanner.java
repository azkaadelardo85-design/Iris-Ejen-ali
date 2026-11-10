package dev.irisejen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IrisScanner {
    private static final String[] DIR = {"Timur", "Tenggara", "Selatan", "Barat Daya", "Barat", "Barat Laut", "Utara", "Timur Laut"};
    private static final List<String> RARITY = List.of("Ancient Debris", "Spawner", "Diamond", "Emerald", "Gold",
            "Lapis", "Redstone", "Iron", "Copper", "Coal", "Peti");

    private record Hit(BlockPos pos, String type) {}

    private IrisScanner() {}

    static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        int i = (int) Math.round(((a + 360) % 360) / 45.0) % 8;
        return DIR[i];
    }

    static String oreName(BlockState st) {
        if (st.is(Blocks.ANCIENT_DEBRIS)) return "Ancient Debris";
        if (st.is(Blocks.SPAWNER)) return "Spawner";
        if (st.is(BlockTags.DIAMOND_ORES)) return "Diamond";
        if (st.is(BlockTags.EMERALD_ORES)) return "Emerald";
        if (st.is(BlockTags.GOLD_ORES)) return "Gold";
        if (st.is(BlockTags.LAPIS_ORES)) return "Lapis";
        if (st.is(BlockTags.REDSTONE_ORES)) return "Redstone";
        if (st.is(BlockTags.IRON_ORES)) return "Iron";
        if (st.is(BlockTags.COPPER_ORES)) return "Copper";
        if (st.is(BlockTags.COAL_ORES)) return "Coal";
        if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST)) return "Peti";
        return null;
    }

    public static void scan(ServerPlayer p, boolean entities, boolean ores) {
        IrisState s = IrisManager.get(p);
        if (s.overloadTicks > 0) { IrisManager.msg(p, "Sistem overload.", ChatFormatting.RED); return; }
        if (s.scanCooldown > 0) { IrisManager.msg(p, "Pemindai sedang dingin.", ChatFormatting.YELLOW); return; }
        double cost = (entities ? 10 : 0) + (ores ? 20 : 0);
        if (s.override) cost *= 0.5;
        if (s.energy < cost) { IrisManager.msg(p, "Energi kurang untuk scan.", ChatFormatting.RED); return; }
        s.energy -= cost;
        s.scanCooldown = 40;
        s.hudUntil = p.tickCount + 80;

        ServerLevel level = (ServerLevel) p.level();
        IrisManager.sound(p, SoundEvents.BEACON_ACTIVATE, 2.0f);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 2, p.getY() + 0.2,
                    p.getZ() + Math.sin(a) * 2, 1, 0, 0.05, 0, 0.01);
        }
        IrisManager.msg(p, "=== PINDAI ===", ChatFormatting.AQUA);
        if (entities) scanEntities(p, s, level);
        if (ores) scanBlocks(p, s, level);
    }

    private static void scanEntities(ServerPlayer p, IrisState s, ServerLevel level) {
        double r = s.override ? 48 : 32;
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && e.isAlive());
        int hostile = 0, other = 0, players = 0;
        List<LivingEntity> hostiles = new ArrayList<>();
        for (LivingEntity e : list) {
            IrisManager.fx(e, MobEffects.GLOWING, 200, 0);
            if (e instanceof Player) players++;
            else if (e instanceof Enemy) { hostile++; hostiles.add(e); }
            else other++;
        }
        IrisManager.msg(p, "Radius " + (int) r + ": " + hostile + " musuh, " + players + " pemain, " + other + " makhluk lain (bersinar 10 detik).",
                hostile > 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
        hostiles.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        for (int i = 0; i < Math.min(3, hostiles.size()); i++) {
            LivingEntity e = hostiles.get(i);
            IrisManager.msg(p, String.format("- %s HP %.0f/%.0f, %.0f blok ke %s", e.getName().getString(),
                    e.getHealth(), e.getMaxHealth(), Math.sqrt(e.distanceToSqr(p)),
                    compass(e.getX() - p.getX(), e.getZ() - p.getZ())), ChatFormatting.GRAY);
        }
    }

    private static void scanBlocks(ServerPlayer p, IrisState s, ServerLevel level) {
        int r = s.override ? 18 : 14;
        BlockPos c = p.blockPosition();
        List<Hit> hits = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!level.hasChunkAt(m)) continue;
                    String name = oreName(level.getBlockState(m));
                    if (name != null) hits.add(new Hit(m.immutable(), name));
                }
            }
        }
        if (hits.isEmpty()) { IrisManager.msg(p, "Tidak ada bijih/struktur dalam radius " + r + ".", ChatFormatting.GRAY); return; }
        hits.sort(Comparator.comparingDouble(h -> h.pos().distSqr(c)));

        Hit best = null;
        int bestRank = 99;
        int lines = 0;
        for (String type : RARITY) {
            int count = 0;
            Hit nearest = null;
            for (Hit h : hits) {
                if (h.type().equals(type)) { count++; if (nearest == null) nearest = h; }
            }
            if (nearest == null) continue;
            int rank = RARITY.indexOf(type);
            if (rank < bestRank) { bestRank = rank; best = nearest; }
            if (lines++ < 7) {
                IrisManager.msg(p, String.format("%s x%d - terdekat %.0f blok ke %s (y=%d)", type, count,
                        Math.sqrt(nearest.pos().distSqr(c)),
                        compass(nearest.pos().getX() - p.getX(), nearest.pos().getZ() - p.getZ()),
                        nearest.pos().getY()), ChatFormatting.GOLD);
            }
        }
        for (int i = 0; i < Math.min(60, hits.size()); i++) {
            BlockPos b = hits.get(i).pos();
            level.sendParticles(ParticleTypes.END_ROD, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
        }
        if (best != null) {
            Vec3 eye = p.getEyePosition();
            Vec3 to = Vec3.atCenterOf(best.pos()).subtract(eye);
            double len = to.length();
            Vec3 dir = to.normalize();
            for (double d = 2; d <= Math.min(len, 12); d += 1.0) {
                Vec3 pt = eye.add(dir.scale(d));
                level.sendParticles(ParticleTypes.GLOW, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
            IrisManager.msg(p, "Penuntun (garis partikel) menuju " + best.type() + " terdekat.", ChatFormatting.AQUA);
        }
    }
}
