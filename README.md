# Margaretha Editor

Editor video Android native, pribadi, offline-first. Tanpa akun, tanpa iklan, tanpa watermark.
Spesifikasi induk: *AURELIA Video Studio — Master Build Specification v1.0* (nama produk diganti menjadi **Margaretha Editor**).

## Status: Phase 1 + Color Studio foundation

`IMPORT → PREVIEW → TRIM → SPLIT → COLOR LOOKS → COMBINE → EXPORT → PLAY EXPORTED MP4`

Lihat [FEATURE_STATUS.md](FEATURE_STATUS.md) untuk status jujur tiap fitur.

## Build

Kebutuhan: JDK 17+, Android SDK (compileSdk 36). Gradle wrapper 9.4.0 sudah disertakan.

```bash
./gradlew :core:timeline:test          # tes mesin timeline (JVM murni, cepat)
./gradlew :app:assembleRelease         # APK → app/build/outputs/apk/release/app-release.apk
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
