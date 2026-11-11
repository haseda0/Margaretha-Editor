# HANDOFF

Diperbarui 2026-11-11. Cabang `ccr-7b30104d-htrl4j`, PR #1.

## Selesai
- Mesin timeline v3 + 61 tes JVM (`./gradlew :core:timeline:test`).
- Shader GLSL grade/blur + 30 pemeriksaan piksel (`node tools/shader-test/run.js`).
- Pipeline render pratinjau/ekspor, UI studio, pustaka media, pengaturan (lihat CHANGELOG 2.0.0).
- CI diperbaiki (akhiran baris LF).

## Keputusan arsitektur
- Tetap Kotlin + Compose + Media3 1.11.1; tidak ada dependensi baru.
- Satu `RenderPlan` (modul inti) dipakai pratinjau dan ekspor. Matematika waktu (`Motion`) ada di inti dan diuji.
- Efek dibangun `StudioEffects.build`; nilainya dibaca per frame, jadi slider tidak membangun ulang pipeline. Pipeline hanya dibangun ulang bila *jenis* efek berubah (`Structure`).
- Waktu efek: pratinjau memakai posisi playhead; ekspor memakai `FrameClock` (dihitung dari frame pertama item) supaya tidak bergantung asal timestamp Media3.
- Teks: `TextRenderer` menghasilkan bitmap yang sama untuk overlay Compose (pratinjau) dan `OverlayEffect` (ekspor).
- Audio trek: pratinjau = ExoPlayer pendamping per trek; ekspor = `EditedMediaItemSequence` audio dengan `addGap`.
- Slider: `vm.draft(edit)` (tanpa riwayat) → `vm.commitDraft()` saat dilepas.

## Belum / dibatasi
- Belum pernah dijalankan di perangkat. Android SDK tidak bisa diunduh di lingkungan kerja (host `dl.google.com` diblokir), jadi verifikasi Android = kompilasi + lint di CI saja.
- Crossfade antar klip, multi-trek video/PiP, keyframe, resolusi pratinjau, foreground service ekspor, EQ/ducking, transkripsi otomatis.
- Efek pada klip foto tidak tampil di pratinjau (tampil di ekspor).

## Bug/risiko yang diketahui
- Klip dengan kecepatan ≠ 1×: waktu transisi/animasi teks/fade audio saat ekspor mungkin terskala oleh faktor kecepatan.
- `addGap` didokumentasikan ambigu (µs vs ms). Bila salah satuan, verifikasi durasi akan menggagalkan ekspor dengan pesan jelas.

## Langkah berikut (urut)
1. Pasang APK dari artifact CI, jalankan skenario di TESTING.md, catat hasil.
2. Perbaiki temuan runtime (mulai dari 3 risiko di FEATURE_STATUS.md).
3. Pindahkan ekspor ke foreground service.
4. Crossfade: sequence video kedua + `VideoCompositorSettings`, atau `CompositionPlayer` untuk pratinjau.
