# Status Fitur

Legenda: ✅ diimplementasi & lulus tes otomatis · 🟡 diimplementasi & dikompilasi CI, belum dijalankan di perangkat · 📋 direncanakan · ⛔ tidak didukung

> Tidak ada bagian Android yang pernah dijalankan di HP fisik atau emulator. Semua baris 🟡 butuh uji perangkat
> sebelum dianggap berfungsi. Mesin timeline (✅) diuji 72 tes JVM; shader GLSL diuji 30 pemeriksaan piksel di WebGL;
> kontrol taktil dirender sungguhan di JVM (`docs/design/tactile-controls.png`).

## Proyek & data
| Fitur | Status | Catatan |
|---|---|---|
| Buat, buka, ganti nama, duplikat, hapus (konfirmasi) | 🟡 | |
| Autosave atomik + cadangan + pemulihan sesi | 🟡 | |
| Format proyek berversi (v4) + migrasi v1–v3 + tolak file rusak | ✅ | `ProjectCodec` |
| Cari & urutkan proyek (terbaru, nama, durasi) | 🟡 | |
| Cadangkan per proyek ke file / semua ke folder; pulihkan tanpa menimpa | 🟡 UI / ✅ codec | Cadangan = susunan suntingan, bukan salinan media |
| Cadangan otomatis ke folder saat keluar dari editor | 🟡 | |
| Onboarding (sekali) | 🟡 | |
| Thumbnail & metadata proyek | 🟡 | |
| Pengaturan: akun, penyimpanan, ekspor, cadangan, proyek baru, cache, privasi, diagnostik | 🟡 | |

## Akun
| Fitur | Status | Catatan |
|---|---|---|
| Daftar, masuk, keluar, verifikasi email, lupa/reset kata sandi | 🟡 kode | **Butuh backend**: proyek Supabase belum dibuat (lihat backend/README.md) |
| Profil, nama tampilan, ganti email (terverifikasi), ganti kata sandi, hapus akun | 🟡 kode | Hapus akun butuh migrasi `0001_account.sql` |
| Sesi tersimpan terenkripsi (Android Keystore), tetap masuk saat offline, sesi kedaluwarsa ditangani | 🟡 | |
| Isolasi proyek per akun; keluar tidak menghapus proyek | ✅ model / 🟡 UI | Proyek lokal (tanpa pemilik) terlihat oleh semua akun di perangkat |
| Build tanpa backend = mode lokal, dinyatakan di UI | 🟡 | Keadaan build CI saat ini |
| Sinkronisasi proyek ke cloud | ⛔ | Sengaja tidak ada; media dan proyek tidak pernah diunggah |

## Penyimpanan pilihan pengguna
| Fitur | Status | Catatan |
|---|---|---|
| Pilih folder lewat pemilih sistem (internal, SD, USB OTG, penyedia dokumen) | 🟡 | Izin persisten; tanpa izin penyimpanan luas |
| Ekspor ke folder pilihan atau Galeri | 🟡 | Jatuh ke Galeri + pemberitahuan bila folder gagal |
| Deteksi izin dicabut / penyimpanan dilepas | 🟡 | |
| Proyek aktif disimpan langsung di folder pilihan | ⛔ | Proyek tetap di penyimpanan aplikasi (tulis atomik); folder menerima cadangan |
| Riwayat ekspor (buka, bagikan) | 🟡 | |

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
| Keyframe posisi/skala/rotasi/opasitas (tambah, hapus, lompat, interpolasi linear, tampil di timeline) | ✅ mesin / 🟡 render & UI | Tetap menempel pada gambar saat trim/belah/ubah kecepatan |
| Keyframe volume dan parameter efek | 📋 | Arsitektur sudah mendukung (nilai dibaca per frame) |
| Ganti media klip | ✅ mesin / 🟡 UI | Dari pustaka media |

## Pratinjau
| Fitur | Status | Catatan |
|---|---|---|
| Putar, jeda, stop, langkah per-frame, scrub | 🟡 | |
| Kecepatan pratinjau 0,5×/1×/2×, layar penuh | 🟡 | |
| Ubah rasio kanvas (9:16, 16:9, 1:1, 4:5, ukuran kustom) | ✅ mesin / 🟡 UI | |
| Roda jog per-frame, penampil timecode | 🟡 | |
| Teks di pratinjau = teks hasil ekspor | 🟡 | Bitmap yang sama dipakai keduanya |
| Efek pada klip **foto** di pratinjau | ⛔ sementara | Foto tampil tanpa grading/efek di pratinjau; di ekspor tetap diterapkan |
| Pilihan resolusi pratinjau | 📋 | |

## Kreatif
| Fitur | Status | Catatan |
|---|---|---|
| 7 color look + intensitas | 🟡 | |
| Histogram / waveform / vectorscope warna | 📋 | Tidak ditampilkan sama sekali (tidak ada scope palsu) |
| Color grading: eksposur, kecerahan, kontras, saturasi, suhu, tint, highlight, bayangan, putih, hitam | 🟡 Android / ✅ shader | Tahan tombol untuk bandingkan dengan asli |
| Efek: blur, tajamkan, vignette, grain | 🟡 Android / ✅ shader | |
| Bayangan/"shadow" sebagai efek terpisah | — | Tercakup oleh kontrol Bayangan di grading |
| Transisi masuk/keluar: fade, slide, zoom, wipe, blur | 🟡 Android / ✅ matematika | Terhadap layar hitam |
| Crossfade/dissolve antar dua klip | ⛔ | Media3 belum bisa menumpuk dua klip dalam satu sequence |
| Posisi, skala, rotasi, flip, opasitas, potong tepi | 🟡 | |
| Teks: font, ukuran, tebal/miring, warna, perataan, rotasi, opasitas, garis tepi, bayangan, latar, posisi, durasi, animasi | 🟡 | |
| Subtitle manual + impor/ekspor SRT | ✅ parser / 🟡 UI | |
| Transkripsi otomatis | ⛔ | Tidak ada mesin pengenal suara terpasang |
| Audio: volume, bisu, fade masuk/keluar, trim, belah, beberapa klip | 🟡 | Volume maksimum 100% |
| Rekam suara (voice-over) ke trek suara | 🟡 | Izin mikrofon diminta saat menekan Rekam |
| EQ, kompresor, ducking | 📋 | |

## Ekspor
| Fitur | Status | Catatan |
|---|---|---|
| MP4 H.264/HEVC + AAC, 720p–4K, 24/25/30/50/60 FPS | 🟡 | Pilihan disaring kemampuan encoder perangkat |
| Preset kualitas Draf / Standar / Tinggi / Maksimum | ✅ rencana / 🟡 UI | |
| APK debug + release dan AAB dibangun & diverifikasi CI | ✅ CI | Release bertanda kunci debug sampai keystore disediakan |
| Semua edit di atas ikut dirender | 🟡 | |
| Progres nyata, batal, pesan gagal, perkiraan ukuran | 🟡 | |
| Verifikasi hasil (video+audio+durasi) sebelum sukses | 🟡 | Toleransi max(250 ms, 2%) |
| Simpan ke Galeri / bagikan / putar hasil | 🟡 | |
| Ekspor tahan aplikasi ditutup (foreground service) | 📋 | Saat ini layar dijaga menyala |
| HDR dipertahankan | ⛔ | Di-tone-map ke SDR |

## Desain
| Fitur | Status | Catatan |
|---|---|---|
| Token warna/material, tiga tingkat kedalaman | ✅ dirender | |
| Knob putar, roda jog, fader alur, saklar, tombol timbul, penampil timecode | ✅ dirender / 🟡 sentuhan | Gestur & haptik belum dicoba di perangkat |
| Mode terang | 📋 | Hanya tema gelap |
| Pengaturan kurangi gerak | — | Aplikasi tidak memakai animasi dekoratif |

## Risiko yang paling perlu diuji di perangkat
1. Waktu efek saat ekspor klip yang **dipercepat/diperlambat** (transisi, animasi teks, fade audio) — bergantung pada urutan internal Media3.
2. Sinkron trek musik di pratinjau (pemutar pendamping) dan jeda diam (`addGap`) saat ekspor.
3. Kinerja pratinjau dengan blur + grading di HP kelas bawah.
