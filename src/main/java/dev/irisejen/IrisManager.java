package dev.irisejen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IrisManager {
    private static final Map<UUID, IrisState> STATES = new HashMap<>();
    static final double REGEN = 0.10;          // per tick (2/detik)
    static final double OVERRIDE_DRAIN = 0.15; // per tick (3/detik)

    private IrisManager() {}

    public static IrisState get(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new IrisState());
    }

    public static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal("[IRIS] " + text).withStyle(color));
    }

    static void sound(ServerPlayer p, SoundEvent ev, float pitch) {
        ((ServerLevel) p.level()).playSound(null, p.getX(), p.getY(), p.getZ(), ev, SoundSource.PLAYERS, 1.0f, pitch);
    }

    static void fx(LivingEntity e, Holder<MobEffect> eff, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(eff, ticks, amp, false, false));
    }

    // ---------- tick ----------
    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p);
    }

    private static void tickPlayer(ServerPlayer p) {
        IrisState s = get(p);
        if (s.scanCooldown > 0) s.scanCooldown--;
        s.cd.replaceAll((k, v) -> Math.max(0, v - 1));
        if (s.overloadTicks > 0) s.overloadTicks--;
        IrisRadar.tick(p, s);

        if (s.override) {
            s.energy -= OVERRIDE_DRAIN;
            if (s.energy <= 0) {
                s.energy = 0;
                endOverride(p, s, true);
            } else if (p.tickCount % 20 == 0) {
                applyOverrideEffects(p);
            }
        } else if (s.overloadTicks == 0 && s.energy < IrisState.MAX) {
            s.energy = Math.min(IrisState.MAX, s.energy + REGEN);
        }

        boolean show = s.override || s.overloadTicks > 0 || s.energy < IrisState.MAX || p.tickCount < s.hudUntil;
        if (show && p.tickCount % 10 == 0 && p.tickCount >= s.alertUntil) showBar(p, s);
    }

    private static void showBar(ServerPlayer p, IrisState s) {
        int filled = (int) Math.round(s.energy / IrisState.MAX * 20);
        ChatFormatting col = s.energy > 50 ? ChatFormatting.AQUA : (s.energy > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);
        MutableComponent c = Component.literal("IRIS ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("\u2588".repeat(filled)).withStyle(col))
                .append(Component.literal("\u2591".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + (int) s.energy + "% ").withStyle(ChatFormatting.WHITE));
        if (s.override) c.append(Component.literal("[OVERRIDE] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        if (s.overloadTicks > 0) c.append(Component.literal("[OVERLOAD " + (s.overloadTicks / 20) + "s] ").withStyle(ChatFormatting.DARK_RED));
        c.append(Component.literal("\u25B6 " + s.selected.label()).withStyle(ChatFormatting.GREEN));
        p.displayClientMessage(c, true);
    }

    // ---------- override ----------
    public static void toggleOverride(ServerPlayer p) {
        IrisState s = get(p);
        if (s.override) { endOverride(p, s, false); return; }
        if (s.overloadTicks > 0) { msg(p, "Sistem overload, tunggu " + (s.overloadTicks / 20) + " detik.", ChatFormatting.RED); return; }
        if (s.energy < 20) { msg(p, "Energi kurang (min 20%) untuk Override.", ChatFormatting.RED); return; }
        s.override = true;
        sound(p, SoundEvents.BEACON_ACTIVATE, 1.4f);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.1);
        applyOverrideEffects(p);
        msg(p, "OVERRIDE AKTIF - speed, lompat, kekuatan, haste, resistance. Skill hemat 50%.", ChatFormatting.RED);
    }

    static void applyOverrideEffects(ServerPlayer p) {
        fx(p, MobEffects.SPEED, 40, 2);
        fx(p, MobEffects.JUMP_BOOST, 40, 1);
        fx(p, MobEffects.STRENGTH, 40, 1);
        fx(p, MobEffects.HASTE, 40, 1);
        fx(p, MobEffects.RESISTANCE, 40, 0);
        fx(p, MobEffects.FIRE_RESISTANCE, 40, 0);
        fx(p, MobEffects.WATER_BREATHING, 40, 0);
        fx(p, MobEffects.NIGHT_VISION, 260, 0);
    }

    static void endOverride(ServerPlayer p, IrisState s, boolean overload) {
        s.override = false;
        p.removeEffect(MobEffects.NIGHT_VISION);
        sound(p, SoundEvents.BEACON_DEACTIVATE, 1.0f);
        if (overload) {
            s.overloadTicks = 300;
            fx(p, MobEffects.SLOWNESS, 300, 1);
            fx(p, MobEffects.WEAKNESS, 300, 1);
            fx(p, MobEffects.MINING_FATIGUE, 300, 0);
            msg(p, "OVERLOAD! Energi habis, sistem terkunci 15 detik.", ChatFormatting.DARK_RED);
        } else {
            msg(p, "Override dimatikan.", ChatFormatting.GRAY);
        }
    }

    // ---------- skills ----------
    public static void select(ServerPlayer p, Skill sk) {
        IrisState s = get(p);
        s.selected = sk;
        s.hudUntil = p.tickCount + 100;
        msg(p, "Skill: " + sk.label() + " - " + sk.desc + " (biaya " + sk.cost + "%)", ChatFormatting.GREEN);
    }

    public static void useSkill(ServerPlayer p, Skill sk) {
        IrisState s = get(p);
        if (s.overloadTicks > 0) { msg(p, "Sistem overload.", ChatFormatting.RED); return; }
        int cd = s.cd.getOrDefault(sk, 0);
        if (cd > 0) { msg(p, sk.label() + " cooldown " + (cd / 20 + 1) + "s", ChatFormatting.YELLOW); return; }
        double cost = s.override ? sk.cost * 0.5 : sk.cost;
        if (s.energy < cost) { msg(p, "Energi kurang untuk " + sk.label(), ChatFormatting.RED); return; }
        if (execute(p, s, sk)) {
            s.energy -= cost;
            s.cd.put(sk, sk.cooldown);
            s.hudUntil = p.tickCount + 60;
        }
    }

    private static boolean execute(ServerPlayer p, IrisState s, Skill sk) {
        ServerLevel level = (ServerLevel) p.level();
        switch (sk) {
            case DASH -> {
                Vec3 look = p.getLookAngle();
                p.setDeltaMovement(look.x * 2.0, Math.max(look.y * 1.2, 0.25), look.z * 2.0);
                p.hurtMarked = true;
                fx(p, MobEffects.SLOW_FALLING, 60, 0);
                sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.6f);
                level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
                return true;
            }
            case BLINK -> {
                Vec3 eye = p.getEyePosition();
                Vec3 look = p.getLookAngle();
                double range = 24;
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                double max = hit.getType() == HitResult.Type.MISS ? range : eye.distanceTo(hit.getLocation());
                for (double d = max - 0.5; d >= 1.5; d -= 0.5) {
                    Vec3 t = eye.add(look.scale(d));
                    double fx = t.x, fy = t.y - p.getEyeHeight(), fz = t.z;
                    AABB bb = p.getBoundingBox().move(fx - p.getX(), fy - p.getY(), fz - p.getZ());
                    if (level.noCollision(p, bb)) {
                        level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                        p.connection.teleport(fx, fy, fz, p.getYRot(), p.getXRot());
                        p.resetFallDistance();
                        sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f);
                        return true;
                    }
                }
                msg(p, "Tidak ada ruang untuk Blink.", ChatFormatting.YELLOW);
                return false;
            }
            case SHIELD -> {
                fx(p, MobEffects.ABSORPTION, 400, 4);
                fx(p, MobEffects.RESISTANCE, 200, 1);
                sound(p, SoundEvents.BEACON_POWER_SELECT, 1.2f);
                level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.9, 0.6, 0.02);
                return true;
            }
            case HEAL -> {
                p.heal(8.0f);
                fx(p, MobEffects.REGENERATION, 100, 3);
                fx(p, MobEffects.SATURATION, 20, 0);
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.8, 0.5, 0.02);
                return true;
            }
            case STEALTH -> {
                fx(p, MobEffects.INVISIBILITY, 600, 0);
                sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f);
                return true;
            }
            case MAGNET -> {
                AABB box = p.getBoundingBox().inflate(14);
                List<Entity> list = level.getEntitiesOfClass(Entity.class, box,
                        e -> e instanceof ItemEntity || e instanceof ExperienceOrb);
                if (list.isEmpty()) { msg(p, "Tidak ada item di sekitar.", ChatFormatting.YELLOW); return false; }
                for (Entity e : list) e.setPos(p.getX(), p.getY(), p.getZ());
                msg(p, list.size() + " objek ditarik.", ChatFormatting.GREEN);
                sound(p, SoundEvents.ENDER_EYE_LAUNCH, 1.2f);
                return true;
            }
            case PULSE -> {
                AABB box = p.getBoundingBox().inflate(7);
                List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != p && e.isAlive() && !(e instanceof Player));
                for (LivingEntity e : list) {
                    Vec3 dir = e.position().subtract(p.position());
                    dir = new Vec3(dir.x, 0, dir.z);
                    dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
                    e.setDeltaMovement(dir.x * 1.3, 0.5, dir.z * 1.3);
                    e.hurtMarked = true;
                    fx(e, MobEffects.SLOWNESS, 100, 3);
                    fx(e, MobEffects.WEAKNESS, 100, 1);
                }
                level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.3f);
                msg(p, list.size() + " target dilumpuhkan.", ChatFormatting.GREEN);
                return true;
            }
            case HOVER -> {
                fx(p, MobEffects.LEVITATION, 25, 4);
                fx(p, MobEffects.SLOW_FALLING, 300, 0);
                sound(p, SoundEvents.BEACON_POWER_SELECT, 1.6f);
                return true;
            }
            case TACTICAL -> {
                fx(p, MobEffects.NIGHT_VISION, 1200, 0);
                List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                        e -> e != p && e.isAlive() && e instanceof Enemy);
                for (LivingEntity e : list) fx(e, MobEffects.GLOWING, 100, 0);
                msg(p, "Mode taktis: " + list.size() + " musuh ditandai.", ChatFormatting.GREEN);
                sound(p, SoundEvents.BEACON_ACTIVATE, 1.8f);
                return true;
            }
            case RECALL -> {
                if (!s.hasMark) { msg(p, "Belum ada titik. Pakai /iris mark.", ChatFormatting.YELLOW); return false; }
                if (!level.dimension().toString().equals(s.markDim)) {
                    msg(p, "Titik ada di dimensi lain.", ChatFormatting.YELLOW);
                    return false;
                }
                p.connection.teleport(s.mx, s.my, s.mz, p.getYRot(), p.getXRot());
                p.resetFallDistance();
                sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f);
                return true;
            }
        }
        return false;
    }

    public static void toggleEvade(ServerPlayer p) {
        IrisState s = get(p);
        s.autoEvade = !s.autoEvade;
        msg(p, "Auto-hindar " + (s.autoEvade ? "AKTIF (biaya 8%, override 4%)" : "mati"),
                s.autoEvade ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    public static void toggleRadar(ServerPlayer p) {
        IrisState s = get(p);
        s.radar = !s.radar;
        msg(p, "Radar ancaman " + (s.radar ? "AKTIF" : "mati"), s.radar ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    public static void mark(ServerPlayer p) {
        IrisState s = get(p);
        s.hasMark = true;
        s.markDim = p.level().dimension().toString();
        s.mx = p.getX();
        s.my = p.getY();
        s.mz = p.getZ();
        msg(p, String.format("Titik ditandai: %.0f, %.0f, %.0f", s.mx, s.my, s.mz), ChatFormatting.GREEN);
    }

    public static void status(ServerPlayer p) {
        IrisState s = get(p);
        msg(p, String.format("Energi %.0f%% | Override %s | Radar %s | Hindar %s | Skill: %s | Titik: %s",
                s.energy, s.override ? "ON" : "off", s.radar ? "ON" : "off", s.autoEvade ? "ON" : "off", s.selected.label(),
                s.hasMark ? String.format("%.0f,%.0f,%.0f", s.mx, s.my, s.mz) : "-"), ChatFormatting.AQUA);
        s.hudUntil = p.tickCount + 100;
    }
}
