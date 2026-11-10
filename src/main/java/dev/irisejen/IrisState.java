package dev.irisejen;

import java.util.EnumMap;

public class IrisState {
    public static final double MAX = 100.0;
    public double energy = MAX;
    public boolean override;
    public int overloadTicks;
    public int scanCooldown;
    public int hudUntil;
    public Skill selected = Skill.DASH;
    public final EnumMap<Skill, Integer> cd = new EnumMap<>(Skill.class);
    public boolean radar = true;
    public boolean autoEvade = true;
    public int evadeCd;
    public int alertUntil;
    public int lastAlertTick = -100;
    public int lastHostile;
    public int lastMobWarn = -1000;
    public boolean hasMark;
    public String markDim = "";
    public double mx, my, mz;
}
