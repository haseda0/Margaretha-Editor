# Timeline Engine

Lokasi: `core/timeline/src/main/kotlin/id/haseda/margaretha/timeline/`

## Waktu
Semua posisi dan durasi = `Long` mikrodetik. Tidak ada float untuk waktu → tidak ada drift (dites: 9 belahan beruntun tetap tepat 1.000.000 µs per potongan).

## Model
- `MediaAsset` — file sumber (uri, durasi, ukuran, rotasi, audio/video, fps).
- `Clip` — `sourceInUs`, `sourceOutUs`, `timelineStartUs`, `volume`, `muted`. Durasi = out − in.
- `Track` — `kind` (MAIN_VIDEO, OVERLAY_VIDEO, AUDIO, TEXT), `magnetic`. Trek magnetik selalu rapat dari 0; posisi diturunkan dari urutan (ripple otomatis).
- `Project` — kanvas (rasio + fps), aset, trek.

Skema v1 sengaja hanya berisi properti yang benar-benar dirender. Speed, transform, crop, effect stack, keyframe ditambahkan bersama implementasi rendernya (skema v2+, dengan migrasi di `ProjectCodec.migrate`).

## Perintah
`AddClips`, `SplitClip`, `TrimClip`, `MoveClip`, `DeleteClips`, `DuplicateClip`, `SetClipAudio`, `RenameProject`, `RelinkAsset`.
`TimelineEngine.apply(project, edit)` → `Applied(project)` atau `Rejected(alasan)`. Murni dan deterministik (ID baru diberikan pemanggil).
Klip minimum 100 ms.

## Undo / redo
`EditorSession.perform(edit)` menyimpan `(label, before, after)`. Undo/redo mengembalikan snapshot immutable persis. Edit yang ditolak tidak masuk riwayat; edit baru menghapus tumpukan redo; batas 200 langkah. Drag handle trim = satu entri (commit saat dilepas).

## Pemetaan waktu
`Track.locate(t)` → (indeks klip, offset). Di akhir trek menjepit ke akhir klip terakhir. Dipakai preview untuk seek global.
