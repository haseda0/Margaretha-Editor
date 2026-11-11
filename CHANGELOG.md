# Changelog

## Quality hardening — 2026-11-10
- Menolak metadata impor dengan URI, nama, MIME type, atau dimensi kosong/nol sebelum project berubah.
- Menolak file project dengan ID aset duplikat sebelum data dapat tertimpa saat didekode.
- Menambah regresi untuk validasi impor dan file project duplikat, serta menjalankannya di GitHub Actions bersama build debug/release dan lint.
- Status build dan tes terbaru tetap harus diambil dari CI; validasi media Android di perangkat fisik masih wajib.

## 1.0.0-phase1 — 2026-11-09
### Ditambahkan
- Mesin timeline `:core:timeline` (µs integer, perintah edit tervalidasi, undo/redo, format proyek v1, rencana export) + 30 tes.
- Beranda: proyek baru (pilih format 9:16/16:9/1:1/4:5 lalu video), daftar proyek, ganti nama, duplikat, hapus, pemulihan sesi.
- Editor: preview ExoPlayer, transport + frame-step, timeline playhead-tengah (scrub, zoom, snap, handle trim), belah, trim ke playhead, pindah, duplikat, hapus, volume/bisu, undo/redo, autosave, tautkan ulang media.
- Export Media3 Transformer: resolusi/FPS/kualitas/codec/audio, filter kemampuan encoder, cek ruang penyimpanan, progres, batal, verifikasi hasil, simpan ke Galeri, putar & bagikan.
- CI GitHub Actions yang membangun dan menerbitkan APK.


## 1.1.0 — Color Studio foundation
- Added seven non-destructive color looks shared by Media3 preview and MP4 export.
- Added per-clip look selection in the editor toolbar.
- Bumped project schema to v2 with automatic v1 migration; look changes support undo/redo.
- Device build and runtime verification are still required.
