package dev.irisejen;

public enum Skill {
    DASH("dash", 15, 40, "Lesatan cepat ke arah pandang"),
    BLINK("blink", 25, 100, "Teleport jarak dekat ke titik yang dilihat"),
    SHIELD("shield", 20, 200, "Perisai energi (absorption + resistance)"),
    HEAL("heal", 25, 300, "Pemulihan darurat"),
    STEALTH("stealth", 20, 400, "Tak terlihat 30 detik"),
    MAGNET("magnet", 10, 100, "Tarik item & XP di sekitar"),
    PULSE("pulse", 20, 120, "Gelombang kejut: dorong + lumpuhkan musuh"),
    HOVER("hover", 15, 200, "Melayang sebentar lalu jatuh pelan"),
    TACTICAL("tactical", 10, 100, "Night vision + tandai musuh sekitar"),
    RECALL("recall", 30, 200, "Kembali ke titik yang ditandai (/iris mark)");

    public final String id;
    public final int cost;
    public final int cooldown;
    public final String desc;

    Skill(String id, int cost, int cooldown, String desc) {
        this.id = id;
        this.cost = cost;
        this.cooldown = cooldown;
        this.desc = desc;
    }

    public String label() { return id.toUpperCase(); }

    public Skill next() { Skill[] v = values(); return v[(ordinal() + 1) % v.length]; }

    public Skill prev() { Skill[] v = values(); return v[(ordinal() + v.length - 1) % v.length]; }
}
