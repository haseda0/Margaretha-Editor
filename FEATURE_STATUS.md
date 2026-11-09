# Status Fitur

Legenda: ✅ Diimplementasi & dites · 🟡 Diimplementasi, belum dites penuh (butuh perangkat) · 📋 Direncanakan · ⛔ Tidak didukung · 🚧 Terblokir batasan teknis terverifikasi

> "Dites" di sini = tes otomatis yang lulus. Fitur Android (🟡) sudah ditulis terhadap API Media3 1.11.1
> yang diverifikasi dari source resmi, dikompilasi di CI, tetapi belum dijalankan di HP fisik.

## Phase 1
| Fitur | Status | Catatan |
|---|---|---|
| Model timeline µs (tanpa drift) | ✅ | 30 tes JVM |
| Tambah / belah / trim / pindah / hapus (ripple) / duplikat | ✅ | Mesin + UI |
| Undo / redo berbasis perintah | ✅ | Snapshot immutable per perintah, batas 200 |
| Format proyek berversi + migrasi + tolak file rusak | ✅ | `ProjectCodec`, skema v1 |
| Rencana export, ukuran output per rasio, estimasi file | ✅ | `ExportPlanner` |
| Import via Photo Picker + izin persisten / salin aman | 🟡 | Fallback salin ke penyimpanan app |
| Probe metadata + cek decoder perangkat | 🟡 | MediaMetadataRetriever + MediaCodecList |
| Thumbnail asinkron + cache LRU | 🟡 | Decode ukuran kecil, maks 2 paralel |
| Preview interaktif (play/pause/seek/frame-step) | 🟡 | ExoPlayer playlist ber-clipping |
| Timeline: scrub, pinch-zoom, snap ke tepi klip, handle trim | 🟡 | |
| Volume & bisu per klip (preview + export) | 🟡 | ChannelMixingAudioProcessor; 0–100% |
| Export MP4 H.264/HEVC + AAC, 720p–4K, 24–60 FPS maks | 🟡 | Opsi difilter kemampuan encoder perangkat |
| Progres, waktu berjalan, batal, coba lagi, penjelasan gagal | 🟡 | |
| Verifikasi hasil (video+audio+durasi) sebelum sukses | 🟡 | Toleransi max(250 ms, 2%) |
| Simpan ke Galeri (Movies/Margaretha Editor) / bagikan / putar hasil | 🟡 | API 29+ MediaStore; 26–28 folder app + FileProvider |
| Autosave atomik + cadangan + pemulihan sesi | 🟡 | |
| Tautkan ulang media hilang | ✅ mesin / 🟡 UI | |
| Kelola proyek: buat, buka, ganti nama, duplikat, hapus (konfirmasi) | 🟡 | |
| Tata letak adaptif portrait/landscape | 🟡 | |

## Phase 2 — Color Looks (source update)
| Fitur | Status | Catatan |
|---|---|---|
| 7 color looks non-destruktif (Original, Cinematic, Vivid, Warm, Cool, Matte Fade, B&W) | 🟡 | Media3 GPU effects diterapkan pada preview per klip dan pipeline MP4 export; perlu uji perangkat |
| Penyimpanan look dalam proyek + migrasi skema v1 → v2 | ✅ kode + tes ditambahkan | Proyek lama otomatis memakai Original |
| Undo/redo perubahan look | ✅ mesin perintah | |

## Belum (sesuai ROADMAP)
| Fitur | Status |
|---|---|
| Multi-track (overlay, PiP, musik, voice-over, SFX) | 📋 Phase 2/4 |
| Waveform, keyframe, speed, crop/rotate/flip | 📋 Phase 2/3 |
| Color Studio, LUT .cube, efek GPU | 📋 Phase 3 |
| Transisi | 📋 Phase 3 — butuh kompositor OpenGL sendiri; Media3 Composition belum mendukung crossfade A/V tumpang-tindih |
| Teks, subtitle, SRT, whisper.cpp | 📋 Phase 3/5 |
| Analisis scene/silence/beat, Smart Edit | 📋 Phase 5 |
| Import gambar sebagai klip | 📋 Phase 2 |
| Backup/restore proyek (ekspor/impor file) | 📋 Phase 2 |
| Export di foreground service (tahan aplikasi ditutup) | 📋 Phase 6 — saat ini layar dijaga menyala; file yatim dibersihkan saat start |
| Video HDR dipertahankan HDR | ⛔ v1 — HDR di-tone-map ke SDR agar campuran klip konsisten |
| Volume > 100% | ⛔ v1 — ExoPlayer preview tidak mendukung gain > 1, demi kesamaan preview/export |

## Audit kualitas Phase 2 — 9 November 2026
- Menambahkan validasi impor di `TimelineEngine.addClips`: ID klip/aset kosong, ID aset duplikat dalam satu batch, konflik ID aset dengan proyek, durasi/metadata tidak valid kini ditolak sebelum state proyek diubah.
- Alasan: mencegah map aset tertimpa diam-diam sehingga klip lama dapat menunjuk ke media yang salah.
- Verifikasi lokal: inspeksi source dan diff dilakukan. Build Gradle/JVM test belum dapat dijalankan di lingkungan audit karena distribusi Gradle 9.5.0 tidak dapat diunduh (`UnknownHostException: services.gradle.org`). Jangan menandai perubahan ini sebagai build-lulus sebelum CI menjalankannya.
