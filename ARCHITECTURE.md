# Arsitektur

## Prinsip
1. **Satu sumber kebenaran**: `EditorSession` (core) memegang `Project` immutable + riwayat perintah.
2. **Non-destruktif**: klip hanya menyimpan referensi sumber + titik in/out. File asli tidak pernah ditulis.
3. **Preview = Export**: keduanya dibangun dari `RenderSegments.of(project)` (pembulatan ms yang sama).
4. **Tidak ada pekerjaan media di main thread**: probe/thumbnail/IO di `Dispatchers.IO`; decode/encode di thread internal ExoPlayer/Transformer.
5. **Gagal dengan jelas**: setiap edit ditolak dengan alasan (`EditOutcome.Rejected`), setiap kegagalan export dijelaskan.

## Modul
| Modul | Isi | Alasan |
|---|---|---|
| `:core:timeline` | Kotlin/JVM murni, nol dependensi | Logika edit bisa diuji cepat tanpa Android |
| `:app` | Compose UI + `media/` + `data/` | Phase 1 belum butuh pemisahan modul lebih banyak (spec: hindari modul berlebihan) |

Paket `:app`: `home/`, `editor/`, `media/` (PreviewController, ExportEngine, MediaProbe, ThumbnailCache, CodecSupport), `data/` (ProjectRepository), `ui/` (tema, ikon, komponen).
Modul fitur (`features/color_studio`, `audio_studio`, …) dipecah saat fitur itu dibangun (Phase 2+).

## Alur data
```
UI (Compose) ──event──▶ EditorViewModel ──Edit──▶ EditorSession ──▶ Project (immutable)
                              │                                         │
                              ├─ autosave (debounce 500 ms) ─▶ ProjectRepository (atomic JSON)
                              ├─ RenderSegments ─▶ PreviewController (ExoPlayer playlist ber-clipping)
                              └─ ExportPlanner ─▶ ExportEngine (Media3 Transformer) ─▶ verifikasi ─▶ MediaStore
```

## Keputusan
- **Persistensi file JSON atomik, bukan Room (Phase 1)**: satu dokumen per proyek, tulis-temp + fsync + rename, cadangan `.bak`. Lebih sederhana dan tahan crash; tanpa KSP. Room dipertimbangkan bila butuh query lintas proyek.
- **DI manual** (`AppContainer`): grafnya kecil; Hilt belum sepadan.
- **ViewModel scope activity** + `open/close`: player tetap hidup saat rotasi; sesi ditutup eksplisit.
- **Ikon in-house**: menghindari artefak material-icons yang sudah tidak dikembangkan.
