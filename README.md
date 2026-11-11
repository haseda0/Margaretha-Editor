# Margaretha Editor

Editor video Android native, pribadi, offline-first. Tanpa akun, tanpa iklan, tanpa watermark.
Spesifikasi induk: *AURELIA Video Studio — Master Build Specification v1.0* (nama produk diganti menjadi **Margaretha Editor**).

## Status: 2.0 Studio

`IMPOR (video/foto/audio) → POTONG → KECEPATAN/POSISI → WARNA & EFEK → TRANSISI → MUSIK → TEKS/SUBTITLE → EKSPOR MP4`

Kode lulus tes JVM dan dibangun CI, tetapi belum diuji di perangkat fisik — lihat [HANDOFF.md](HANDOFF.md).

Lihat [FEATURE_STATUS.md](FEATURE_STATUS.md) untuk status jujur tiap fitur.

## Build

Kebutuhan: JDK 17+, Android SDK (compileSdk 36). Gradle wrapper 9.4.0 sudah disertakan.

```bash
./gradlew :core:timeline:test          # tes mesin timeline (JVM murni, cepat)
./gradlew :app:assembleRelease         # APK → app/build/outputs/apk/release/app-release.apk
node tools/shader-test/run.js          # kompilasi + uji piksel shader GLSL di WebGL headless (butuh Playwright)
```

CI GitHub Actions (`.github/workflows/build.yml`) menjalankan tes, membangun APK debug + release,
mengunggahnya sebagai artifact, dan pada `main` menerbitkan GitHub Release berisi `app-release.apk`
yang bisa langsung diunduh dari HP.

APK release ditandatangani dengan debug key (distribusi pribadi). Untuk distribusi lebih luas,
tambahkan keystore sendiri.

## Struktur

```
app/              UI Compose + layanan media Android (preview, export, probe, thumbnail, storage)
core/timeline/    Mesin timeline Kotlin murni: model µs, perintah edit, undo/redo, format proyek, rencana export
docs/             Pipeline render, timeline, export, format proyek
```

Dokumen: [ARCHITECTURE](ARCHITECTURE.md) · [ROADMAP](ROADMAP.md) · [DEPENDENCIES](DEPENDENCIES.md) ·
[LICENSES](LICENSES.md) · [TESTING](TESTING.md) · [CHANGELOG](CHANGELOG.md)
