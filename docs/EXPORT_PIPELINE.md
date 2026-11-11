# Export Pipeline

1. **Rencana** — `ExportPlanner.plan(project, settings)`: validasi (timeline kosong, aset hilang, fps), ukuran output dari rasio kanvas + sisi pendek (genap), bitrate = w·h·fps·bpp(kualitas)·faktor codec (dibatasi 1–100 Mbps), estimasi ukuran (+2% kontainer).
2. **Kemampuan perangkat** — `CodecSupport` membaca `MediaCodecList`: resolusi yang tidak bisa di-encode (`areSizeAndRateSupported`, juga orientasi tertukar) dinonaktifkan; HEVC hanya muncul bila ada encodernya.
3. **Cek ruang** — butuh ≈ 2× estimasi + 50 MB; kalau kurang, export tidak dimulai dan alasannya ditampilkan.
4. **Render** — Media3 Transformer:
   - `EditedMediaItem` per segmen: clipping ms, `Presentation.createForWidthAndHeight(w, h, LAYOUT_SCALE_TO_FIT)`, `setFrameRate(fps)` (maksimum, tidak menaikkan fps sumber), volume via `ChannelMixingAudioProcessor`.
   - `EditedMediaItemSequence.withAudioAndVideoFrom(...)`: klip tanpa audio menyumbang hening → A/V tetap sinkron.
   - `Composition.setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)`.
   - Encoder: `DefaultEncoderFactory` + bitrate diminta + fallback aktif; video H.264/HEVC, audio AAC.
   - Ditulis ke `cache/export/render-*.mp4`.
5. **Progres** — `getProgress` tiap 200 ms; waktu berjalan; layar dijaga menyala.
6. **Verifikasi** — `MediaMetadataRetriever`: ada video, ada audio, durasi cocok rencana (toleransi max(250 ms, 2%)). Gagal → file dihapus, pesan jelas.
7. **Publikasi** — API 29+: `MediaStore` `Movies/Margaretha Editor` dengan `IS_PENDING` (baris dihapus bila salin gagal). API 26–28: folder Movies milik app + `FileProvider` untuk berbagi.
8. **Batal/gagal** — `Transformer.cancel()`, file sementara dihapus; file yatim dari proses yang mati dibersihkan saat editor dibuat.

Kode error `ExportException` dipetakan per rentang (IO 2xxx, decoder 3xxx, encoder 4xxx, frame 5xxx, audio 6xxx, muxer 7xxx) ke penjelasan berbahasa Indonesia + detail teknis.
Hasil tidak pernah dilabeli lossless; resolusi lebih tinggi dari sumber tidak diklaim menambah detail.
