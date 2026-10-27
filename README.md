# Bil Elektrik 3 Rumah — Versi Android OCR 2.0

**Bahasa Malaysia · Kegunaan sendiri · Tanpa langganan Manus.**

Aplikasi Android untuk pembahagian bil elektrik berdasarkan jumlah kWh bil Sabah Electricity dan bacaan dua mini meter:
- **Zul**: mini meter kiri
- **Saldi**: mini meter kanan
- **Puan Maznah**: penggunaan baki (bil utama − Zul − Saldi)

## Fungsi siap di dalam kod

- OCR **Google ML Kit Text Recognition (bundled)** dari gambar bil dan meter. Model OCR disertakan dalam APK dan tidak memerlukan internet untuk digunakan.
- Boleh ambil foto melalui kamera atau pilih foto dari galeri.
- OCR menyenaraikan angka yang dikesan dan membolehkan anda **mengetik angka yang betul**; untuk bil, pilih kWh dan RM berasingan.
- Bacaan boleh diisi / dibetulkan secara manual, termasuk apabila OCR tersalah baca.
- Pengiraan berasaskan **jumlah perlu dibayar**, selepas sebarang kredit.
- Sejarah bulanan disimpan setempat menggunakan WebView localStorage (gambar tidak disimpan).
- Fungsi kongsi melalui WhatsApp / aplikasi lain dan salin teks.
- Tidak menggunakan server atau akaun dan tiada iklan.

**PENTING:** Kod projek OCR ini siap ditulis dan fungsi pengiraan telah diuji menggunakan Node.js, tetapi fail APK belum dapat dikompil atau diuji pada telefon dalam sesi penyediaan ini. Projek memerlukan muat turun komponen binaan Android buat kali pertama.

## Cara A — Dapatkan APK percuma menggunakan GitHub Actions (tidak perlu PC / Manus)

1. Cipta repositori **baharu** di GitHub anda. (Jika repositori awam, semak dahulu supaya anda selesa kod projek dikongsi; jangan masukkan gambar bil peribadi.)
2. Ekstrak ZIP dan masukkan **isi dalam folder `Bil_3_Rumah_OCR_Android`** ke akar repositori, termasuk `.github/workflows/bina-apk.yml`.
3. Pergi ke tab **Actions** → **Bina APK Android Percuma** → **Run workflow** (atau ia bermula selepas push ke `main`).
4. Selepas workflow berjaya, buka run itu → **Artifacts** → **Bil-Elektrik-3-Rumah-OCR-APK**.
5. Muat turun artifak ZIP, ekstrak fail `app-debug.apk`, pindahkan ke Android dan pasang.

GitHub Actions tertakluk kepada had / polisi penggunaan akaun GitHub anda. APK jenis `debug` sudah ditandatangani secara automatik untuk pemasangan peribadi, tetapi **bukan untuk penerbitan Play Store**.

## Cara B — Bina menggunakan Android Studio percuma

1. Muat turun dan ekstrak ZIP.
2. Buka folder `Bil_3_Rumah_OCR_Android` dalam Android Studio.
3. Projek mengandungi fail Gradle tetapi **tidak menyertakan Gradle Wrapper JAR**. Jika Android Studio meminta wrapper, cipta wrapper dengan Gradle 8.7 menggunakan perintah `gradle wrapper --gradle-version 8.7`, atau cipta projek baru Android Studio dan salin folder `app/src/main`, fail `app/build.gradle` dan akar `build.gradle/settings.gradle`.
4. Gunakan JDK 17+, Android SDK API 35, dan benarkan sync yang memerlukan internet pada kali pertama.
5. Pilih **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. Cari `app/build/outputs/apk/debug/app-debug.apk`, pindahkan dan pasang pada telefon.

## Contoh pengiraan

| Komponen | Nilai |
|--|--:|
| Penggunaan bil utama | 454 kWh |
| Jumlah bil semasa | RM160.18 |
| Kredit / terlebih bayar | RM23.23 |
| **Jumlah perlu dibayar** | **RM136.95** |
| Zul: 1631.7 − 1420.9 | 210.8 kWh → **RM63.59** |
| Saldi: 765.5 − 650.6 | 114.9 kWh → **RM34.66** |
| Puan Maznah: baki | 128.3 kWh → **RM38.70** |
| **Jumlah pembahagian** | **RM136.95** |

## Ketepatan OCR

OCR tidak sentiasa tepat untuk meter digital tujuh segmen, foto kabur/silau, bil yang berbeza susun atur atau nombor banyak. Itulah sebabnya **pengguna wajib menyemak nilai sebelum menekan 'Kira Pembahagian'**. Gambar diproses setempat; imej tidak dihantar ke cloud. Bagi bil, hasil OCR **tidak mengisi angka secara automatik** — anda pilih calon untuk mengelakkan silap membaca kredit sebagai amaun perlu dibayar.

## Ujian logik (pilihan)

Pembetulan binaan: fail `file_paths.xml` diletakkan dalam `app/src/main/res/xml/`
supaya Android dapat menemui sumber `@xml/file_paths`. Workflow turut menjalankan
ujian pengiraan sebelum membina APK. Had masa binaan ialah 20 minit, cache Gradle
dimatikan dan artifak APK disimpan selama 3 hari untuk mengehadkan penggunaan.

Setiap binaan pada runner baharu menghasilkan kunci debug baharu. APK daripada
binaan seterusnya mungkin tidak boleh dipasang sebagai kemas kini kepada APK lama.
Simpan atau kongsi rekod penting sebelum menyahpasang aplikasi lama kerana rekod
setempat akan dipadam apabila aplikasi dinyahpasang.

Jika Node.js tersedia, jalankan:
```
node tests/test_logic.js
```

Komponen Android: Java WebView + native Google ML Kit OCR; UI HTML/CSS/JS tersimpan di `app/src/main/assets`.
