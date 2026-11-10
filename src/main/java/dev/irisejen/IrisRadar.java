package dev.irisejen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Radar ancaman pasif: deteksi musuh dekat, pemanah (bow/crossbow), panah/proyektil datang, dan auto-hindar. */
public final class IrisRadar {
    private IrisRadar() {}

    public static void tick(ServerPlayer p, IrisState s) {
        if (!s.radar || s.overloadTicks > 0) return;
        ServerLevel level = (ServerLevel) p.level();
        if (s.evadeCd > 0) s.evadeCd--;
        checkProjectiles(p, s, level);
        if (p.tickCount % 10 == 0) checkMobs(p, s, level);
    }

    private static void alert(ServerPlayer p, IrisState s, String text, ChatFormatting color, boolean sound) {
        if (p.tickCount - s.lastAlertTick < 8) return;
        s.lastAlertTick = p.tickCount;
        s.alertUntil = p.tickCount + 30;
        p.displayClientMessage(Component.literal("\u26A0 " + text).withStyle(color, ChatFormatting.BOLD), true);
        if (sound) IrisManager.sound(p, SoundEvents.BELL_BLOCK, 1.5f);
    }

    // ---------- mob dekat & pemanah ----------
    private static void checkMobs(ServerPlayer p, IrisState s, ServerLevel level) {
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                e -> e != p && e.isAlive() && e instanceof Enemy);
        int hostile = list.size();
        int archers = 0, aimingAtMe = 0;
        LivingEntity nearestArcher = null;
        for (LivingEntity e : list) {
            boolean bow = e.getMainHandItem().is(Items.BOW) || e.getMainHandItem().is(Items.CROSSBOW);
            if (!bow) continue;
            archers++;
            if (nearestArcher == null || e.distanceToSqr(p) < nearestArcher.distanceToSqr(p)) nearestArcher = e;
            if (e instanceof Mob m && m.getTarget() == p) {
                aimingAtMe++;
                IrisManager.fx(e, MobEffects.GLOWING, 60, 0);
            }
        }
        if (aimingAtMe > 0 && nearestArcher != null) {
            alert(p, s, aimingAtMe + " pemanah MEMBIDIK kamu! (" + (int) Math.sqrt(nearestArcher.distanceToSqr(p)) + " blok "
                    + IrisScanner.compass(nearestArcher.getX() - p.getX(), nearestArcher.getZ() - p.getZ()) + ")",
                    ChatFormatting.RED, true);
        } else if (hostile > s.lastHostile || (hostile > 0 && p.tickCount - s.lastMobWarn > 200)) {
            s.lastMobWarn = p.tickCount;
            String t = hostile + " musuh terdeteksi";
            if (archers > 0) t += " (" + archers + " pemanah)";
            alert(p, s, t, archers > 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW, hostile > s.lastHostile);
        }
        s.lastHostile = hostile;
    }

    // ---------- panah / proyektil ----------
    private static void checkProjectiles(ServerPlayer p, IrisState s, ServerLevel level) {
        Vec3 pos = p.getBoundingBox().getCenter();
        List<Projectile> list = level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(24),
                pr -> pr.getOwner() != p && pr.getDeltaMovement().lengthSqr() > 0.09);
        Projectile worst = null;
        double worstT = 999;
        for (Projectile pr : list) {
            Vec3 v = pr.getDeltaMovement();
            double t = pos.subtract(pr.position()).dot(v) / v.lengthSqr();
            if (t < 0 || t > 25) continue;
            Vec3 closest = pr.position().add(v.scale(t));
            if (closest.distanceTo(pos) > 1.6) continue;
            if (t < worstT) { worstT = t; worst = pr; }
        }
        if (worst == null) return;

        boolean dodged = false;
        if (worstT <= 6) {
            Vec3 v = worst.getDeltaMovement();
            Vec3 side = new Vec3(-v.z, 0, v.x);
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            if (side.dot(pos.subtract(worst.position())) < 0) side = side.scale(-1);
            dodged = dodge(p, s, level, side);
        }
        if (dodged) alert(p, s, "AUTO-HINDAR!", ChatFormatting.GREEN, false);
        else alert(p, s, "PANAH DATANG! (" + Math.max(1, (int) Math.round(worstT)) + " tick)", ChatFormatting.RED, true);
    }

    private static boolean dodge(ServerPlayer p, IrisState s, ServerLevel level, Vec3 dir) {
        if (!s.autoEvade || s.evadeCd > 0) return false;
        double cost = s.override ? 4 : 8;
        if (s.energy < cost) return false;
        Vec3 pick = null;
        for (Vec3 c : new Vec3[]{dir, dir.scale(-1)}) {
            if (level.noCollision(p, p.getBoundingBox().move(c.scale(1.5)))) { pick = c; break; }
        }
        double y = 0.3;
        if (pick == null) { pick = Vec3.ZERO; y = 0.65; } // terjepit: lompat saja
        p.setDeltaMovement(pick.x * 0.95, y, pick.z * 0.95);
        p.hurtMarked = true;
        s.energy -= cost;
        s.evadeCd = 15;
        IrisManager.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f);
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 8, 0.2, 0.1, 0.2, 0.03);
        return true;
    }
}
