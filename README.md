# Iris Agent System (Fabric, Minecraft 26.3)
Mod fan-made, tidak resmi. Tanpa aset/teks berhak cipta; semua nama & efek orisinal.

## Kontrol (bisa diganti di Controls > Agen Iris)
R scan | Z override | X pakai skill | C/V ganti skill | B auto-hindar
Atau lewat perintah: /iris scan [entities|ores], /iris override, /iris use [skill], /iris select <skill>, /iris next|prev|list|mark|status

## Fitur
- Scan: musuh/pemain/mob bersinar menembus tembok, info HP+arah, scan bijih/spawner/peti + garis penuntun partikel
- Override: speed, jump, strength, haste, resistance, night vision, dll; skill hemat 50%; energi habis = OVERLOAD (lumpuh 15 detik)
- 10 skill: dash, blink, shield, heal, stealth, magnet, pulse, hover, tactical, recall
- Energi 0-100% tampil di action bar, regen otomatis

- Radar ancaman (pasif): peringatan musuh dekat, pemanah (bow/crossbow) + yang membidik kamu (bersinar), dan PANAH/proyektil datang
- Auto-hindar: loncat menyamping otomatis dari panah (biaya 8% energi, 4% saat override). /iris evade, /iris radar untuk on/off

## Build
Butuh JDK 25 + Gradle 9.7.1.  `gradle build` -> build/libs/irisejen-1.0.0.jar
Tanpa PC: upload folder ini ke GitHub, tab Actions > build > unduh artifact jar.
Pasang: Fabric Loader 0.19.3+, Fabric API untuk 26.3, taruh jar di folder mods.
