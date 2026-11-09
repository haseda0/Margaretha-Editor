# Roadmap

| Fase | Isi | Status |
|---|---|---|
| 0 | Audit lingkungan, arsitektur, verifikasi dependensi | ✅ |
| 1 | Import → preview → trim/split/gabung → export → putar hasil; simpan/buka proyek | 🟡 kode selesai, menunggu uji perangkat |
| 2 | Multi-track (overlay/PiP/musik/voice-over), waveform, drag-reorder, keyframe dasar, gambar, backup proyek | 📋 |
| 3 | Color Studio (shader GLSL non-destruktif), LUT .cube, crop/transform, teks, transisi via kompositor OpenGL/EGL | 📋 |
| 4 | Audio Studio: EQ, kompresor, limiter, HPF/LPF, ducking, deteksi beat | 📋 |
| 5 | whisper.cpp lokal (ID/EN), deteksi scene/silence, saran edit, Auto Edit yang bisa diedit | 📋 |
| 6 | Foreground service export, profil export, optimasi memori, uji durasi panjang | 📋 |
| 7 | QA penuh, audit lisensi/privasi, rilis | 📋 |

## Milestone terkecil berikutnya
Uji APK Phase 1 di HP nyata (skenario 1, 2, 7, 8, 10, 13–15 di TESTING.md), perbaiki temuan,
lalu mulai Phase 2 dengan **trek musik** (sequence audio kedua di `Composition`, preview via `CompositionPlayer`).
