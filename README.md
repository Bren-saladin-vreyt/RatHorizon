# SvregHorizon

"Distant Horizons versi kita" untuk shaderpack **SvregSky Shader Remade** (Iris, Fabric, Minecraft 1.21.11).
Mod ini mengirim peta tinggi dunia ke shader lewat tekstur dinamis di TextureManager (tanpa mixin ke Iris).

## Tahap 1: mod uji
Mod mendaftarkan dua tekstur 512x512 yang gambarnya bergeser tiap detik:
- `svreghorizon:dynamic/heightmap` (A)
- `svreghorizon:textures/heightmap.png` (B)

Shaderpack (opsi **Tes Tekstur Mod**) menampilkan kotak di pojok kanan atas:
- **A** (garis merah) = `customTexture.flTestA` → id A
- **B** (garis hijau) = `customTexture.flTestB` → id B
- **C** (garis biru) = `texture.composite.flTestC` → id A (sintaks lain)

Di bawah tiap kotak ada bar tes filter: kiri = hasil baca tekstur, kanan = abu-abu pembanding.
Kiri **abu-abu sama kayak kanan** = filter LINEAR (yang dibutuhkan tahap 2); kiri **hitam/putih** = NEAREST.

Arti isi kotak:
- gradasi pelangi yang geser tiap detik = tekstur kebaca dan ikut ke-update
- gradasi diam = kebaca tapi nggak ke-update
- kotak-kotak ungu-hitam = id nggak ketemu (tekstur "missing")
- hitam/putih polos = sampler nggak kepasang

## Build
Tiap push ke `main` dibuild GitHub Actions; jar ada di release `latest`.
