# Format Proyek (skema v1)

File: `files/projects/<id>/project.json` (+ `project.json.bak` = versi baik sebelumnya).

```json
{
  "format": "margaretha-editor-project",
  "schemaVersion": 1,
  "id": "uuid", "name": "Liburan", "createdAtMs": 0, "updatedAtMs": 0,
  "canvas": { "aspect": "PORTRAIT_9_16", "frameRate": 30 },
  "assets": [{ "id", "uri", "displayName", "mimeType", "durationUs", "width", "height",
               "rotationDegrees", "hasVideo", "hasAudio", "frameRate" }],
  "tracks": [{ "id": "track-main", "kind": "MAIN_VIDEO", "magnetic": true,
               "clips": [{ "id", "assetId", "sourceInUs", "sourceOutUs", "timelineStartUs", "volume", "muted" }] }]
}
```

## Aturan
- `format` harus cocok; `schemaVersion` > versi aplikasi → ditolak dengan pesan "dibuat oleh versi lebih baru".
- Versi lebih lama → `ProjectCodec.migrate` bertahap.
- Field tak dikenal diabaikan (kompatibel maju untuk field opsional).
- Setelah decode, `ProjectValidator` memeriksa: tepat satu trek utama, ID unik, aset ada, klip dalam durasi aset, trek magnetik rapat. Gagal → proyek tidak dimuat; repository mencoba `.bak`.

## Penyimpanan atomik
tulis `project.json.tmp` → `fsync` → `project.json` lama jadi `.bak` → rename tmp → `project.json`. Crash di titik mana pun menyisakan versi baru atau cadangan; `.tmp` sisa dibersihkan saat aplikasi mulai.

## Media
URI dari Photo Picker disimpan dengan izin baca persisten. Bila provider menolak, video disalin ke `files/media/<assetId>.mp4` dan proyek merujuk salinan itu. File asli tidak pernah diubah.
