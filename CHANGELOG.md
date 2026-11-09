# Changelog

## 1.0.0-phase1 — 2026-11-09
### Ditambahkan
- Mesin timeline `:core:timeline` (µs integer, perintah edit tervalidasi, undo/redo, format proyek v1, rencana export) + 30 tes.
- Beranda: proyek baru (pilih format 9:16/16:9/1:1/4:5 lalu video), daftar proyek, ganti nama, duplikat, hapus, pemulihan sesi.
- Editor: preview ExoPlayer, transport + frame-step, timeline playhead-tengah (scrub, zoom, snap, handle trim), belah, trim ke playhead, pindah, duplikat, hapus, volume/bisu, undo/redo, autosave, tautkan ulang media.
- Export Media3 Transformer: resolusi/FPS/kualitas/codec/audio, filter kemampuan encoder, cek ruang penyimpanan, progres, batal, verifikasi hasil, simpan ke Galeri, putar & bagikan.
- CI GitHub Actions yang membangun dan menerbitkan APK.
