# Rendering Pipeline

## Sumber bersama
`RenderSegments.of(project)` → daftar `(uri, startMs, endMs, volume)` dari trek utama. Pembulatan µs→ms dilakukan sekali di sini; potongan hasil belah berbagi batas ms yang sama sehingga tidak ada celah/tumpang tindih.

## Preview (`PreviewController`)
- ExoPlayer, playlist `MediaItem` dengan `ClippingConfiguration(startMs, endMs)` per segmen.
- `SeekParameters.EXACT` untuk seek akurat frame; scrub di-throttle ~25 seek/detik, seek final saat jari dilepas.
- Playlist dibangun ulang hanya bila struktur berubah; perubahan volume diterapkan langsung (`player.volume` per item saat transisi).
- Posisi global = awal segmen + posisi di item; dipublikasikan tiap 16 ms saat memutar.
- Kanvas: `PlayerView` RESIZE_MODE_FIT di dalam kotak rasio kanvas (letterbox) = setara `Presentation.LAYOUT_SCALE_TO_FIT` di export.

## Export
Lihat EXPORT_PIPELINE.md. Segmen yang sama → `EditedMediaItem` dengan clipping identik.

## Batasan terverifikasi
Media3 `Composition` belum mendukung crossfade audio/video yang tumpang tindih. Transisi (Phase 3) akan memakai kompositor OpenGL/EGL sendiri; tidak ada transisi yang ditampilkan sebelum bekerja di preview **dan** export.
