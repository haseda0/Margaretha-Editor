# Pengujian

## Otomatis (JVM) — `./gradlew :core:timeline:test`
65 tes. Hasil harus selalu diambil dari eksekusi saat ini atau CI; jumlah tes bukan bukti bahwa APK terbaru berfungsi.
- **TimelineEngineTest (17)**, **EditorSessionTest (3)**, **ProjectCodecTest (7)**, **ExportPlannerTest (7)**: perilaku Phase 1 (tata letak, belah, trim, ripple, undo/redo, codec, rencana ekspor).
- **SpeedTest (4)**: durasi & ripple setelah ubah kecepatan, belah pada klip dipercepat memotong frame sumber yang benar, batas kecepatan, segmen render.
- **ImageClipTest (1)**: durasi awal foto dan perpanjangan lewat trim.
- **VisualPropertyTest (3)**: grading/efek/transform/transisi tersimpan, bisa di-undo, masuk rencana render; nilai di luar batas ditolak; belah hanya menyisakan transisi/fade di tepi luar.
- **AudioTrackTest (6)**: trek dibuat otomatis, anti-tumpuk, geser, trim kepala menjaga ekor, audio dipotong ke panjang program, trek bisu, trek terkunci.
- **TextTrackTest (4)**: tambah/ubah/hapus teks + undo, validasi, trek tersembunyi, impor subtitle tambah/ganti, urutan trek.
- **SrtTest (3)**: file SRT berantakan (BOM, CRLF, tanpa indeks, tag, tanpa baris kosong), blok rusak dilewati, format bolak-balik.
- **MotionTest (3)**: kurva fade, status transisi, pose animasi teks.
- **RenderStructureTest (4)**: klip tanpa edit tidak memakai GPU, tiap edit hanya menyalakan tahap yang perlu, ubah nilai tidak mengubah struktur, lapisan teks tidak pernah bertumpuk.
- **StudioCodecTest (3)**: semua field baru bolak-balik, migrasi v2 → v3, file tidak konsisten ditolak.

## Shader — `node tools/shader-test/run.js`
Mengompilasi file GLSL yang sama dengan yang dipakai aplikasi di Chromium headless (WebGL 1 = GLSL ES 1.00) dan memeriksa 30 hasil piksel:
netral = identitas, arah tiap kontrol grading, vignette, grain, tajamkan, fade, wipe masuk/keluar, opasitas, blur horizontal/vertikal, semua uniform aktif.
Ini membuktikan shader benar secara bahasa dan matematika; **bukan** bukti berjalan di GPU HP tertentu.

## CI
Workflow GitHub Actions: tes JVM → `assembleDebug` + `assembleRelease` + `lintDebug` → artifact debug/release APK dan laporan lint.

## Manual di perangkat (wajib sebelum Phase 1 dinyatakan selesai)
| # | Skenario | Hasil |
|---|---|---|
| 1 | Trim satu video lalu export | belum |
| 2 | Gabung beberapa klip berurutan | belum |
| 7 | Sumber portrait → kanvas 16:9 | belum |
| 8 | Sumber landscape → kanvas 9:16 | belum |
| 9 | Frame rate berbeda dalam satu timeline | belum |
| 10 | Video ber-rotasi metadata | belum |
| 11 | Video panjang (>10 menit) | belum |
| 12 | Penyimpanan hampir penuh (pesan jelas) | belum |
| 13 | Aplikasi ditinggal saat export | belum |
| 14 | Paksa tutup lalu buka lagi (pemulihan sesi) | belum |
| 15 | Batalkan export (tidak ada file sisa) | belum |
| 16 | Codec tak didukung (ditolak saat import) | belum |
| 17 | Sumber HDR (tone-map ke SDR) | belum |

| 3 | Musik + fade, lalu export: suara musik mulai di posisi yang benar | belum |
| 4 | Teks & subtitle (impor SRT): posisi/waktu di hasil = pratinjau | belum |
| 5 | Grading + efek + transisi pada klip video | belum |
| 6 | Klip 2× dan 0,5× dengan transisi + fade audio | belum |
| 18 | Foto di antara video, kanvas 9:16 | belum |
| 19 | Posisi/skala/rotasi/potong tepi | belum |
| 20 | Proyek v2 lama dibuka di versi ini | belum |
Setelah export: putar hasil di aplikasi, cek durasi = timeline, audio sinkron, rasio benar.
