# Changelog

## 2.0.0-studio — 2026-11-11
### Ditambahkan
- **Mesin timeline v3** (skema proyek v3, migrasi otomatis dari v1/v2): kecepatan klip 0,25×–4×, posisi/skala/rotasi/flip/opasitas, potong tepi, color grading 10 kontrol, efek (blur, tajamkan, vignette, grain), transisi masuk/keluar (fade, slide, zoom, wipe, blur), fade audio, klip foto, dua trek audio bebas, trek teks dan subtitle, kunci/sembunyikan trek, ubah kanvas, impor/ekspor SRT.
- **Pipeline render**: shader GLSL sendiri untuk grading/efek/transisi + blur terpisah, transform relatif kanvas, teks dibakar ke video lewat overlay GPU, trek audio sebagai sequence tambahan, fade audio akurat per sampel. Pratinjau dan ekspor memakai rantai efek yang sama.
- **UI studio**: tab alat (Edit, Audio, Teks, Efek, Warna, Media) dengan panel inline, slider pratinjau langsung (satu langkah undo saat dilepas), bandingkan sebelum/sesudah, timeline multi-baris (teks, subtitle, musik, suara) yang bisa digeser dan diubah panjangnya, waveform audio, seret teks di pratinjau, pustaka media dengan cari/filter, pengaturan, layar penuh, kecepatan pratinjau, tombol stop, pintasan keyboard.
- Impor foto (JPG/PNG/WebP) dan audio (MP3/WAV/M4A, sesuai dukungan perangkat).

### Diubah
- Menghapus klip tidak lagi membuang medianya dari proyek; media tetap di pustaka sampai dihapus sendiri.
- Durasi proyek ditentukan trek video utama; audio dan teks yang melewatinya dipotong saat render.

### Diperbaiki
- CI gagal sejak sumber diunggah dengan akhiran baris CRLF (`/bin/sh^M`); seluruh repo dinormalisasi ke LF.

### Belum diverifikasi
- Belum ada bagian Android yang dijalankan di perangkat fisik. Lihat HANDOFF.md.

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
