# Backend akun (Supabase)

Aplikasi berjalan penuh tanpa backend (mode lokal). Untuk mengaktifkan daftar/masuk:

1. Buat proyek Supabase (paket gratis cukup).
2. **SQL Editor** → jalankan `supabase/migrations/0001_account.sql`.
3. **Authentication → URL Configuration → Redirect URLs** → tambahkan `margaretha://auth`.
   Tanpa ini, tautan verifikasi email dan reset kata sandi tidak kembali ke aplikasi.
4. **Authentication → Providers → Email**: biarkan "Confirm email" menyala.
5. Salin *Project URL* dan *publishable/anon key* ke `backend.properties` (lihat `backend.properties.example`)
   atau ke variabel/secret GitHub `MARGARETHA_SUPABASE_URL` dan `MARGARETHA_SUPABASE_ANON_KEY`, lalu build ulang.

Yang dikirim ke server: email, kata sandi (ke Supabase Auth lewat HTTPS), dan nama tampilan.
Proyek, video, foto, dan audio tidak pernah diunggah.

Jangan pernah memasukkan `service_role` key ke aplikasi atau repo.
