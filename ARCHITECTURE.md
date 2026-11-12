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

Paket `:app`:
| Paket | Isi |
|---|---|
| `account/` | `AuthApi` (Supabase Auth lewat HTTPS), `SessionStore` (AES-GCM, kunci di Android Keystore), `AccountRepository`, layar masuk/daftar/akun |
| `data/` | `ProjectRepository` (JSON atomik, lingkup pemilik, impor cadangan), `UserStorage` (folder pilihan lewat SAF), `AppSettings`, `ExportHistory` |
| `media/` | `PreviewController`, `ExportEngine`, `StudioEffects` (shader), `TextRenderer`, `AudioFx`, `MediaProbe`, `ThumbnailCache`, `WaveformCache`, `VoiceRecorder` |
| `editor/` | Layar editor, timeline, panel alat, `EditorViewModel` |
| `home/` | Beranda, pengaturan, onboarding, `HomeViewModel` |
| `ui/` | Token desain, ikon, komponen, kontrol taktil (`Tactile.kt`) |

Logika bisnis tidak berada di composable: semua perubahan proyek adalah perintah `Edit` yang divalidasi mesin inti.

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

## Render (2.x)
- `RenderSegments.plan(project)` menghasilkan `RenderPlan` (segmen video, trek audio, teks). Pratinjau dan ekspor sama-sama membacanya.
- `EffectStructure` menentukan tahap GPU mana yang ada; nilai di dalam tahap dibaca per frame, jadi slider dan keyframe tidak membangun ulang pipeline.
- Waktu: pratinjau memakai posisi playhead; ekspor memakai `FrameClock` per item. Matematika waktu (`Motion`, `Keyframes`) ada di modul inti.
- Keyframe disimpan dalam **waktu sumber**, sehingga trim, belah, dan ubah kecepatan tidak perlu menulis ulang keyframe.

## Akun & kepemilikan
- Proyek membawa `ownerId`. Daftar proyek hanya memuat proyek milik akun aktif dan proyek lokal (tanpa pemilik); editor menolak membuka proyek akun lain.
- Keluar tidak menghapus apa pun. Hapus akun dilakukan fungsi server `delete_own_account` (hanya bisa menyentuh `auth.uid()`).
- Hanya kunci publishable yang ada di APK. Tanpa konfigurasi, `AccountState.Unconfigured` dan aplikasi berjalan mode lokal.

## Penyimpanan
- Bawaan: proyek di penyimpanan aplikasi, ekspor ke Galeri (MediaStore).
- Folder pilihan (SAF, izin persisten): `MargrethaEditor/{Exports,Backups,Projects}`. Tanpa izin penyimpanan luas.
- Media tidak diduplikasi: proyek menyimpan URI + izin baca persisten; salinan hanya dibuat bila penyedia menolak izin jangka panjang.

## Desain
Tiga tingkat kedalaman dengan cahaya dari kiri-atas: permukaan struktural rata, kontrol timbul (`raised`), detail presisi cekung (`recessed`). Kontrol taktil di `ui/Tactile.kt` murni Compose dan bisa dirender di JVM lewat `tools/ui-shot` (lihat `docs/design/tactile-controls.png`).
