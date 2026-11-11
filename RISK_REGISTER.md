# Daftar risiko

| # | Risiko | Dampak | Kemungkinan | Mitigasi yang sudah ada | Tindakan berikut |
|---|---|---|---|---|---|
| R1 | Kode Android belum pernah dijalankan di perangkat | Tinggi | Pasti ada temuan | Logika dipusatkan di modul inti teruji; shader diuji di WebGL; ekspor memverifikasi file sebelum menyatakan sukses | Uji perangkat (TESTING.md) |
| R2 | Waktu efek saat ekspor klip dengan kecepatan ≠ 1× | Sedang | Sedang | Jam per item dihitung dari frame pertama, tidak bergantung asal timestamp | Uji skenario 6 |
| R3 | Satuan `addGap` Media3 ambigu di dokumentasi | Sedang | Rendah | Verifikasi durasi menggagalkan ekspor dengan pesan jelas bila salah | Uji skenario 3 |
| R4 | Sinkron trek audio di pratinjau (pemutar pendamping) | Rendah–sedang | Sedang | Koreksi drift > 120 ms; ekspor tidak terpengaruh | Uji dengar |
| R5 | Kinerja pratinjau dengan blur + grading di HP kelas bawah | Sedang | Sedang | Blur 13 tap terpisah; klip tanpa edit melewati GPU sepenuhnya | Tambah opsi resolusi pratinjau |
| R6 | Backend akun belum dikonfigurasi | Sedang | Pasti (saat ini) | Aplikasi berjalan mode lokal dan mengatakannya di UI | Ikuti backend/README.md |
| R7 | Tautan verifikasi/pemulihan tidak kembali ke aplikasi | Sedang | Sedang | Deep link `margaretha://auth` terdaftar | Tambah Redirect URL di Supabase |
| R8 | Ekspor terhenti bila aplikasi ditutup | Sedang | Sedang | Layar dijaga menyala; file yatim dibersihkan | Foreground service |
| R9 | Folder pilihan dilepas/izin dicabut | Rendah | Sedang | Status terdeteksi; ekspor jatuh ke Galeri dengan pemberitahuan | — |
| R10 | Rilis ditandatangani kunci debug | Sedang (Play) | Pasti tanpa keystore | CI menerima keystore lewat secret | Sediakan keystore |
| R11 | Efek pada klip foto tidak tampil di pratinjau | Rendah | Pasti | Didokumentasikan; tampil di ekspor | Rute foto lewat pipeline efek |
| R12 | Kehilangan data proyek | Tinggi | Rendah | Tulis atomik + `.bak`, pemulihan sesi, cadangan ke folder, pulihkan tanpa menimpa | — |
