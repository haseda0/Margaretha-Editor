# Pengujian

## Otomatis (JVM) — `./gradlew :core:timeline:test`
34 tes didefinisikan. Hasil harus selalu diambil dari eksekusi saat ini atau CI; jumlah tes bukan bukti bahwa build atau APK terbaru sudah lolos.
- **TimelineEngineTest (17)**: tata letak rapat, tolak media tanpa video/terlalu pendek, tolak metadata nol/ID aset duplikat secara atomik, belah (posisi sumber benar, tanpa celah), belah di klip kedua, belah terlalu dekat tepi, trim + ripple, trim di luar sumber, pindah urutan, hapus + ripple + buang aset tak terpakai, aset tetap bila dipakai potongan lain, duplikat, validasi audio, ganti nama, `locate` + clamp, 9 belahan beruntun tanpa drift, relink.
- **EditorSessionTest (3)**: undo/redo memulihkan state persis, edit baru menghapus redo, edit ditolak tidak tercatat, batas riwayat.
- **ProjectCodecTest (7)**: round-trip lengkap (unicode, kutip, newline), input rusak/terpotong/tidak konsisten → gagal anggun, ID aset duplikat ditolak sebelum dapat menimpa referensi, skema lebih baru ditolak dengan pesan, migrasi skema v1 → v2 memakai look Original, undo/redo look, field tak dikenal diabaikan.
- **ExportPlannerTest (7)**: ukuran output 9:16/16:9/1:1/4:5/4K/720p, durasi & bitrate & estimasi ukuran, timeline kosong, klip bisu → volume 0, segmen hasil belah tetap rapat setelah pembulatan ms, toleransi verifikasi durasi, format timecode.

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

Skenario 3–6 (musik, teks, subtitle, multi-track) menunggu fiturnya (Phase 2–3).
Setelah export: putar hasil di aplikasi, cek durasi = timeline, audio sinkron, rasio benar.
