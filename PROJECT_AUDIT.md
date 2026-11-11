# Audit proyek

Diperbarui 2026-11-11.

## Yang ditemukan
| Aspek | Temuan |
|---|---|
| Platform | Android native, minSdk 26, target/compile 36 |
| Bahasa & UI | Kotlin 2.3.0, Jetpack Compose (BOM 2026.04.01), Material 3 |
| Media | AndroidX Media3 1.11.1 (ExoPlayer, Transformer, Effect) |
| Build | Gradle 9.5.0 (wrapper), AGP 9.3.2, katalog versi, 2 modul: `:app`, `:core:timeline` |
| Arsitektur | MVVM + StateFlow, kontainer dependensi manual, mesin timeline Kotlin murni tanpa Android |
| Persistensi | File JSON berversi per proyek, tulis atomik + cadangan `.bak` |
| Autentikasi | Tidak ada |
| Tes | 34 tes JVM di mesin timeline; tidak ada tes instrumen |
| CI | Build APK debug/release + lint + tes; **rusak**: `gradlew` ber-CRLF (`/bin/sh^M`) |
| Fitur berjalan (kode) | Impor video, preview, belah/trim/pindah/duplikat/hapus, volume, 7 look warna, undo/redo, autosave, ekspor MP4 terverifikasi |
| Belum pernah | Dijalankan di perangkat fisik (dinyatakan di dokumen asli) |

## Keputusan
- **Pertahankan stack.** Kotlin + Compose + Media3 sudah tepat untuk editor video Android; tidak ada alasan migrasi.
- **Tanpa Room/DataStore/DI framework/WorkManager.** Data proyek adalah satu dokumen per proyek, bukan data relasional; file JSON atomik + codec berversi yang sudah teruji lebih sederhana dan lebih mudah dicadangkan ke folder pengguna. Preferensi kecil cukup SharedPreferences. Graf dependensi kecil ditangani kontainer manual.
- **Tanpa pemecahan menjadi belasan modul Gradle.** Pemisahan dilakukan per paket (`account`, `data`, `media`, `editor`, `home`, `ui`) dan satu modul inti murni yang teruji; modul lebih banyak hanya menambah waktu build tanpa manfaat pada ukuran ini.
- **Backend akun: Supabase Auth** lewat REST (tanpa SDK tambahan). Alasan: email+kata sandi, verifikasi, pemulihan, dan RLS tersedia; kunci publishable aman di klien; tidak mengikat ke Google Play Services.

## Batasan lingkungan kerja
- Android SDK tidak bisa diunduh (host `dl.google.com` diblokir), tidak ada emulator. Kompilasi Android, lint, APK dan AAB dibuktikan di GitHub Actions.
- Yang bisa dijalankan lokal: tes JVM mesin timeline, uji piksel shader di WebGL headless, render komponen desain di Compose Desktop.
- Pembuatan proyek Supabase lewat konektor ditolak izin; backend harus dibuat pemilik (lihat `backend/README.md`).
