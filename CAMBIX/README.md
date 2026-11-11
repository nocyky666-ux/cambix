# CAMBIX 2.0.0

Kamera dan studio foto Android dari Shii Dev. Paket `com.shii.dev.cambix`, minimum Android 10 (API 29).

## Yang sudah dikerjakan

| Area | Isi |
|---|---|
| Foto | Camera2, JPEG sampai sekitar 12 MP, rasio dipilih dari sensor. |
| Live Photo | Encoder H.264 perangkat keras menyimpan beberapa detik terakhir. Saat tombol ditekan, klip mulai dari keyframe sebelum tombol. Hasilnya satu file Motion Photo (format Google). 30 fps, tanpa audio. |
| Video | MediaRecorder + Camera2. Pilihan 1080p atau 4K, 30 atau 60 fps, H.264 atau H.265. Pilihan hanya aktif jika kamera, durasi frame, dan encoder semuanya cocok. Jika tidak, turun otomatis dan layar menjelaskan alasannya. |
| Pro | ISO, rana, fokus manual, EV. **RAW**: simpan DNG + JPEG sekaligus bila kamera melaporkan kemampuan RAW. |
| Look dan filter live | Pratinjau GPU (OpenGL ES): look, LUT 3D, eksposur, kontras, sorotan, bayangan, suhu, tint, saturasi, vibrance, pudar, vignet, grain. Rumus yang sama dipakai saat menyimpan, jadi pratinjau dan file cocok. |
| Simpan dengan filter | Tiga pilihan: pratinjau saja, terapkan ke foto, atau simpan asli + hasil edit. |
| LUT | Impor dan ekspor `.cube` (3D, sampai 65 titik). 25 look bawaan yang dibuat sendiri (bukan salinan profil film berlisensi). Intensitas, favorit, kategori. |
| Monitor | Histogram RGB, waveform, vektorskop, peringatan highlight dan bayangan terpotong, zebra, peaking fokus, warna palsu, level horizon dari sensor. |
| Jelajah | Katalog look dengan thumbnail dari foto terbaru Anda, favorit, impor LUT dan preset, ekspor, hapus, pilih look untuk kamera. |
| Studio | Editor tanpa merusak file asli: cahaya, warna, HSL 8 kanal, look/LUT, efek (vignet, grain, pudar, ketajaman, kejelasan, glow, aberasi RGB, sinar matahari simulasi), putar, cermin, pangkas rasio, banding dengan asli, undo/redo, pemulihan proyek, preset, ekspor JPEG atau PNG resolusi penuh. |
| Galeri | Filter Foto/Live/Video/RAW/Favorit, putar Live Photo dan video, EXIF, favorit, bagikan, hapus, edit di Studio. |
| Koneksi AI | Penyedia buatan sendiri: OpenAI-compatible, Gemini, REST kustom (template + path jawaban). Kunci API terenkripsi di Android Keystore, tidak ikut diekspor. HTTPS wajib kecuali diizinkan per penyedia. Gambar hanya dikirim setelah persetujuan setiap kali. Dipakai di Studio untuk saran koreksi warna dan tips komposisi. |

## Belum ada (jujur)

Mode Night, Portrait, Macro; HDR; slow motion dan time-lapse; bracketing dan burst; filter yang dibakar ke video
(video selalu disimpan tanpa filter, begitu juga Live Photo); layer dan masking; relighting/depth AI; super-resolution;
pengembangan RAW sendiri (DNG dibuka di Studio lewat dekoder Android, bukan demosaic CAMBIX); Vulkan dan NDK; marketplace
online; pemisahan multi-modul; ColorMatch dari foto referensi; waveform RGB parade.

## Cara mendapat APK

Kode ini ditulis di lingkungan tanpa Android SDK dan internet, jadi **belum pernah dikompilasi atau dijalankan di perangkat**.
Build pertama adalah ujian pertamanya.

**Lewat GitHub (cukup dari HP):**
1. Buat repo baru di GitHub, unggah seluruh isi folder ini (termasuk `.github`).
2. Tab Actions, jalankan "Build APK" (juga otomatis setelah push ke `main`).
3. Setelah hijau, unduh artifact `cambix-debug-apk`, ekstrak, pasang `app-debug.apk`.
4. Kalau merah, salin log bagian yang gagal dan kirim.

**Android Studio:** buka folder, tunggu sync, Run. Atau `gradle :app:assembleDebug` (Gradle 8.9, JDK 17).

**Release bertanda tangan:** buat `keystore.properties` di root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`),
lalu `gradle :app:assembleRelease`. Tanpa file itu, release tidak ditandatangani.

## Daftar uji di perangkat (belum dijalankan siapa pun)

1. Kamera terbuka, preview tegak, tidak melar. Cek di dua kamera.
2. Jelajah: pilih "Teal & Orange", kembali ke Kamera. Viewfinder berubah warnanya. Matikan di Setelan, preview tetap normal.
3. Panel Look di kamera: slider Koreksi mengubah preview. Pilih "Terapkan" di tab Simpan, ambil foto, buka di Galeri: warnanya sama dengan preview.
4. Impor LUT `.cube` dari file manager, pilih, cek hasilnya. Coba file rusak: harus muncul pesan, bukan crash.
5. Monitor: histogram bergerak saat kamera digerakkan, zebra muncul di langit terang, peaking di tepi tajam.
6. Pro + RAW (jika kamera mendukung): ambil foto, cek ada `.dng` dan `.jpg` di Pictures/CAMBIX, buka DNG di Studio atau aplikasi lain.
7. Video: coba 4K 60. Chip dan panel menunjukkan yang benar-benar aktif. Rekam 10 detik, periksa info file di galeri sistem.
8. Live Photo: tunggu 2 detik, tekan tombol, putar di Galeri dan di Google Photos.
9. Studio: buka foto, geser slider, tahan tombol banding, undo/redo, tutup lalu buka foto yang sama (proyek pulih), ekspor, cek file baru dan file asli tidak berubah.
10. Koneksi AI: tambah penyedia, "Ambil daftar model", "Tes koneksi", lalu "Saran AI" di Studio. Dialog persetujuan harus muncul sebelum gambar dikirim.
11. Setelan > "Salin laporan perangkat" dan kirim jika ada yang aneh.

## Batas yang perlu diketahui

- Pratinjau GPU mengasumsikan sensor berorientasi portrait (90 atau 270 derajat), seperti hampir semua ponsel. Jika GPU gagal, kamera otomatis memakai pratinjau biasa tanpa filter live.
- Zebra, peaking, dan warna palsu hanya ada di pratinjau GPU.
- RAW + pratinjau + JPEG adalah kombinasi stream yang tidak semua perangkat izinkan. Jika sesi ditolak, RAW dimatikan otomatis dengan catatan.
- Tap-to-focus memetakan sentuhan ke koordinat sensor dengan asumsi UI portrait; belum diverifikasi di banyak perangkat.
- Zoom dibaca dari kamera logis; Camera2 tidak membedakan optik dan digital, jadi app tidak mengklaim optik.
- Galeri hanya menampilkan file yang dibuat app ini.
- AndroidManifest mengizinkan cleartext di level OS supaya server lokal bisa dipakai; kode app tetap menolak http:// kecuali diizinkan per penyedia.
- Aplikasi klien tetap bisa dibongkar. Untuk distribusi publik, arahkan Base URL AI ke proxy backend milik Anda.
