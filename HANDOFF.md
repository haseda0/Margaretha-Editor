# HANDOFF

Diperbarui 2026-11-11. Cabang `ccr-7b30104d-htrl4j`, PR #1.

## Selesai
- Mesin timeline v4 + 72 tes JVM (`./gradlew :core:timeline:test`).
- Shader GLSL + 30 pemeriksaan piksel (`node tools/shader-test/run.js`).
- Kontrol desain dirender di JVM (`cd tools/ui-shot && gradle shot -Pout=out`).
- Pipeline render, UI studio, akun, penyimpanan SAF, keyframe, voice-over, onboarding, pengaturan (lihat CHANGELOG).
- CI membangun APK debug/release + AAB dan memverifikasi artifact.

## Blocker eksternal (butuh pemilik)
1. **Backend akun**: buat proyek Supabase, jalankan `backend/supabase/migrations/0001_account.sql`, tambahkan Redirect URL `margaretha://auth`, isi `MARGARETHA_SUPABASE_URL` (variable) dan `MARGARETHA_SUPABASE_ANON_KEY` (secret) di GitHub. Sampai itu, build berjalan mode lokal.
2. **Keystore rilis**: secret `MARGARETHA_KEYSTORE_BASE64`, `MARGARETHA_KEYSTORE_PASSWORD`, `MARGARETHA_KEY_ALIAS`, `MARGARETHA_KEY_PASSWORD`. Tanpa itu release bertanda kunci debug (bisa dipasang, tidak bisa diunggah ke Play).
3. **Uji perangkat**: belum ada satu pun kode Android yang dijalankan di HP/emulator.

## Keputusan arsitektur
Lihat ARCHITECTURE.md dan PROJECT_AUDIT.md. Ringkas: stack dipertahankan; JSON atomik (bukan Room); REST langsung ke Supabase (bukan SDK); keyframe dalam waktu sumber; efek membaca nilai per frame.

## Belum / dibatasi
- Crossfade antar klip, multi-trek video/PiP, keyframe volume/efek, scope warna, resolusi pratinjau, foreground service ekspor, mode terang, EQ/ducking, transkripsi otomatis, sinkronisasi cloud.
- Efek pada klip foto tidak tampil di pratinjau (tampil di ekspor).

## Bug/risiko yang diketahui
Lihat RISK_REGISTER.md (R1–R12).

## Langkah berikut (urut)
1. Pasang APK dari artifact CI, jalankan skenario TESTING.md, catat hasil, perbaiki temuan.
2. Selesaikan blocker 1 dan 2, uji alur akun end-to-end.
3. Foreground service ekspor → crossfade → opsi resolusi pratinjau.
