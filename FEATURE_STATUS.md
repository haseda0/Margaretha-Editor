# Status Fitur

Legenda: ✅ diimplementasi & lulus tes otomatis · 🟡 diimplementasi & dikompilasi CI, belum dijalankan di perangkat · 📋 direncanakan · ⛔ tidak didukung

> Tidak ada bagian Android yang pernah dijalankan di HP fisik atau emulator. Semua baris 🟡 butuh uji perangkat
> sebelum dianggap berfungsi. Mesin timeline (✅) diuji 61 tes JVM; shader GLSL diuji 30 pemeriksaan piksel di WebGL.

## Proyek & data
| Fitur | Status | Catatan |
|---|---|---|
| Buat, buka, ganti nama, duplikat, hapus (konfirmasi) | 🟡 | |
| Autosave atomik + cadangan + pemulihan sesi | 🟡 | |
| Format proyek berversi (v3) + migrasi v1/v2 + tolak file rusak | ✅ | `ProjectCodec` |
| Thumbnail & metadata proyek | 🟡 | |
| Pengaturan (frame rate proyek baru, bersihkan cache, tentang) | 🟡 | |

## Media
| Fitur | Status | Catatan |
|---|---|---|
| Impor video (MP4/MOV/WebM sesuai decoder perangkat) | 🟡 | Codec tak didukung ditolak dengan alasan |
| Impor foto (JPG/PNG/WebP) sebagai klip | 🟡 | Durasi awal 3 dtk, bisa ditarik sampai 10 menit |
| Impor audio (MP3/WAV/M4A sesuai perangkat) | 🟡 | Lewat pemilih dokumen |
| Pustaka media: info, cari, filter, pakai ulang, hapus | 🟡 UI / ✅ mesin | |
| Tautkan ulang media hilang | 🟡 UI / ✅ mesin | |
| Drag & drop dari aplikasi lain | 📋 | |

## Timeline
| Fitur | Status | Catatan |
|---|---|---|
| Belah, trim, pindah urutan, duplikat, hapus (ripple) | ✅ mesin / 🟡 UI | |
| Kecepatan klip 0,25×–4× | ✅ mesin / 🟡 render | Suara ikut berubah tempo |
| Trek teks, subtitle, musik, suara/efek | ✅ mesin / 🟡 UI | Geser & ubah panjang dengan seret |
| Kunci & sembunyikan/bisukan trek | ✅ mesin / 🟡 UI | |
| Zoom, scrub, snap, playhead | 🟡 | |
| Waveform audio | 🟡 | Didekode di latar; file > 30 menit tanpa waveform |
| Undo/redo (200 langkah) | ✅ | |
| Beberapa trek video / picture-in-picture | 📋 | Butuh kompositor multi-layer |
| Keyframe | 📋 | |

## Pratinjau
| Fitur | Status | Catatan |
|---|---|---|
| Putar, jeda, stop, langkah per-frame, scrub | 🟡 | |
| Kecepatan pratinjau 0,5×/1×/2×, layar penuh | 🟡 | |
| Ubah rasio kanvas (9:16, 16:9, 1:1, 4:5) | ✅ mesin / 🟡 UI | |
| Teks di pratinjau = teks hasil ekspor | 🟡 | Bitmap yang sama dipakai keduanya |
| Efek pada klip **foto** di pratinjau | ⛔ sementara | Foto tampil tanpa grading/efek di pratinjau; di ekspor tetap diterapkan |
| Pilihan resolusi pratinjau | 📋 | |

## Kreatif
| Fitur | Status | Catatan |
|---|---|---|
| 7 color look | 🟡 | |
| Color grading: eksposur, kecerahan, kontras, saturasi, suhu, tint, highlight, bayangan, putih, hitam | 🟡 Android / ✅ shader | Tahan tombol untuk bandingkan dengan asli |
| Efek: blur, tajamkan, vignette, grain | 🟡 Android / ✅ shader | |
| Bayangan/"shadow" sebagai efek terpisah | — | Tercakup oleh kontrol Bayangan di grading |
| Transisi masuk/keluar: fade, slide, zoom, wipe, blur | 🟡 Android / ✅ matematika | Terhadap layar hitam |
| Crossfade/dissolve antar dua klip | ⛔ | Media3 belum bisa menumpuk dua klip dalam satu sequence |
| Posisi, skala, rotasi, flip, opasitas, potong tepi | 🟡 | |
| Teks: font, ukuran, tebal/miring, warna, perataan, garis tepi, bayangan, latar, posisi, durasi, animasi | 🟡 | |
| Subtitle manual + impor/ekspor SRT | ✅ parser / 🟡 UI | |
| Transkripsi otomatis | ⛔ | Tidak ada mesin pengenal suara terpasang |
| Audio: volume, bisu, fade masuk/keluar, trim, beberapa klip | 🟡 | Volume maksimum 100% |
| EQ, kompresor, ducking | 📋 | |

## Ekspor
| Fitur | Status | Catatan |
|---|---|---|
| MP4 H.264/HEVC + AAC, 720p–4K, 24/25/30/50/60 FPS | 🟡 | Pilihan disaring kemampuan encoder perangkat |
| Semua edit di atas ikut dirender | 🟡 | |
| Progres nyata, batal, pesan gagal, perkiraan ukuran | 🟡 | |
| Verifikasi hasil (video+audio+durasi) sebelum sukses | 🟡 | Toleransi max(250 ms, 2%) |
| Simpan ke Galeri / bagikan / putar hasil | 🟡 | |
| Ekspor tahan aplikasi ditutup (foreground service) | 📋 | Saat ini layar dijaga menyala |
| HDR dipertahankan | ⛔ | Di-tone-map ke SDR |

## Risiko yang paling perlu diuji di perangkat
1. Waktu efek saat ekspor klip yang **dipercepat/diperlambat** (transisi, animasi teks, fade audio) — bergantung pada urutan internal Media3.
2. Sinkron trek musik di pratinjau (pemutar pendamping) dan jeda diam (`addGap`) saat ekspor.
3. Kinerja pratinjau dengan blur + grading di HP kelas bawah.
