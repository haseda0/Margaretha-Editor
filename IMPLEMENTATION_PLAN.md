# Rencana implementasi

Status per 2026-11-11. Detail per fitur ada di FEATURE_STATUS.md.

| # | Tonggak | Status |
|---|---|---|
| 1 | Audit repo, perbaiki CI | selesai |
| 2 | Mesin timeline v3: kecepatan, transform, grading, efek, transisi, audio, teks, SRT | selesai, teruji |
| 3 | Pipeline render bersama (preview + ekspor), shader GLSL | selesai, dikompilasi CI, shader teruji di WebGL |
| 4 | UI studio: tab alat, panel, timeline multi-baris, pustaka media | selesai, dikompilasi CI |
| 5 | Mesin v4: keyframe, intensitas look, rotasi teks, kanvas kustom, ganti media, pemilik proyek | selesai, teruji |
| 6 | Akun (Supabase Auth), sesi terenkripsi, isolasi proyek per akun | kode selesai; butuh konfigurasi backend |
| 7 | Penyimpanan pilihan pengguna (SAF), cadangan/pulihkan, riwayat ekspor | selesai, dikompilasi CI |
| 8 | Sistem desain taktil: knob, jog wheel, fader, saklar | selesai, dirender dan dikritik |
| 9 | Voice-over, onboarding, pengaturan, cari/urut proyek | selesai, dikompilasi CI |
| 10 | CI: APK debug/release + AAB + verifikasi artifact | selesai |
| 11 | **Uji di perangkat** dan perbaikan temuan runtime | belum — butuh HP/emulator |
| 12 | Ekspor di foreground service; crossfade; multi-trek video; scope warna | belum |

## Urutan setelah ini
1. Uji perangkat mengikuti TESTING.md; perbaiki temuan.
2. Konfigurasi backend dan uji alur akun end-to-end.
3. Keystore rilis sendiri untuk AAB yang bisa diunggah ke Play.
4. Butir 12.
