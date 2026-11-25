# SvregHorizon (repo RatHorizon)

"Distant Horizons versi kita" untuk shaderpack **SvregSky Shader Remade** (Iris, Fabric, Minecraft 1.21.11).
Mod ini nyimpen peta permukaan dunia asli di sekitar pemain (1 titik tiap 8x8 blok, 512x512 titik = 4096x4096 blok)
dan ngirim ke shader sebagai tekstur dinamis `svreghorizon:dynamic/heightmap` (di shader: `customTexture.flMap`).
Tanpa mixin ke Iris.

## Sumber data
- **Chunk yang dimuat** (singleplayer dan server): permukaan asli, termasuk pohon dan bangunan.
- **Generator dunia** (khusus singleplayer): tinggi tanah ditanya langsung ke generator tanpa bikin chunk, dari
  dekat ke jauh, di thread belakang (kerja 5 ms, istirahat 8 ms).
- Semua disimpan per dunia + dimensi di `.minecraft/svreghorizon/` (file tile 64x64 titik), jadi besok nggak ngulang.

## Format tekstur (per piksel)
Peta nempel ke dunia dan muter (wrap): titik peta (tx, tz) = blok 8tx..8tx+7, ada di piksel (tx mod 512, tz mod 512).
- R, G = tinggi + 128 (byte atas, byte bawah)
- B = jenis biome (lihat `Biomes.java`, shader punya daftar yang sama)
- A = 128 diketahui | 64 air | 16 tanda | periode z << 2 | periode x (kelipatan 512 titik, buat nolak piksel basi)

## Build
Tiap push ke `main` dibuild GitHub Actions; jar ada di release `latest`.

## Perintah di chat

- `/horizon` — status peta: sumber data, sampai mana generator, kecepatannya, error kalau ada
- `/horizon jarak <256-2048>` — seberapa jauh generator dunia ngisi peta (blok). Makin kecil makin cepet selesai dan makin enteng
- `/horizon generator on|off` — nyalain/matiin generator dunia (singleplayer)

Seberapa jauh daratan jauh *digambar* diatur di shader: menu Daratan Jauh → Jarak.
Setelan disimpan di `.minecraft/config/svreghorizon.properties`.
