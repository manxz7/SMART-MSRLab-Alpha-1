# Deploy: Tukar Qwen Vision dari RunPod ke OpenRouter

Server: `halal-app.smartnode.my` (backend FastAPI dalam Docker)
Anggaran masa: 10–15 minit. Database **tidak** disentuh.

## Apa yang berubah

Scan Produk, Scan Logo dan Scan Premis guna model **Qwen3-VL** untuk membaca gambar.
Dulu model ini berjalan di RunPod (sudah ditutup). Sekarang ia dipanggil melalui
**OpenRouter** (API berbayar ikut penggunaan, tiada GPU diperlukan di server).

| Fail | Perubahan |
|---|---|
| `backend/api.py` | URL, API key dan nama model dibaca dari environment variable |
| `backend/docker-compose.yml` | Hantar `OPENROUTER_API_KEY` dan `VISION_MODEL` ke container `api` |

Tiada library baru. `requirements.txt` dan `Dockerfile` tidak berubah. Fail `jakim_cb.csv` sedia ada di server tidak terjejas.

## Environment variable

| Nama | Wajib | Nilai |
|---|---|---|
| `OPENROUTER_API_KEY` | Ya | Diberi secara berasingan (`sk-or-v1-...`) |
| `VISION_MODEL` | Tidak | Lalai `qwen/qwen3-vl-8b-instruct` |

Jangan tulis key dalam `api.py` atau commit ke Git.

## Langkah (Docker Compose melalui SSH)

```bash
# 1. Pergi ke folder backend yang sedang berjalan
#    (jika tak pasti: docker inspect <nama_container_api> --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}')
cd /path/ke/backend

# 2. Backup
cp api.py api.py.backup
cp docker-compose.yml docker-compose.yml.backup

# 3. Ambil kod baru
git pull origin master
#    Jika folder ini bukan git repo: salin backend/api.py dari GitHub sahaja.

# 4. Tambah key ke fail .env (sebelah docker-compose.yml)
echo 'OPENROUTER_API_KEY=sk-or-v1-...' >> .env

# 5. Bina semula container backend SAHAJA (database tidak disentuh)
docker compose up -d --build api

# 6. Semak log
docker logs -f <nama_container_api>
```

**Jika `docker-compose.yml` di server berbeza dari GitHub** (contoh: konfigurasi DB lain),
kekalkan versi server dan tambah dua baris ini sahaja di bawah `environment:` service `api`:

```yaml
      OPENROUTER_API_KEY: ${OPENROUTER_API_KEY}
      VISION_MODEL: ${VISION_MODEL:-qwen/qwen3-vl-8b-instruct}
```

### Jika guna panel (Dokploy / Coolify / Portainer)
1. App backend → **Environment** → tambah `OPENROUTER_API_KEY=sk-or-v1-...`
2. **Redeploy** dari branch `master`.

## Cara sahkan berjaya

1. Log semasa start **tidak** mengandungi:
   `[Vision] AMARAN: OPENROUTER_API_KEY tidak ditetapkan`
2. Uji dari terminal dengan mana-mana gambar produk:
   ```bash
   curl -F "file=@produk.jpg" https://halal-app.smartnode.my/scan-image
   ```
   Jawapan sepatutnya ada `"vision_result": {"brand": "...", ...}` (tidak kosong)
   dan `"detection_method": "vision"`.
3. Log menunjukkan `[Vision] Sending image to qwen/qwen3-vl-8b-instruct...` diikuti jawapan JSON.

| Ralat dalam log | Punca |
|---|---|
| `401 User not found` | Key salah / sudah dipadam |
| `402` | Akaun OpenRouter tiada kredit |
| `vision_result: {}` dan `detection_method: "ocr"` | Key tidak sampai ke container — semak `.env` / environment |

## Rollback

```bash
cp api.py.backup api.py
cp docker-compose.yml.backup docker-compose.yml
docker compose up -d --build api
```

## Kos

Lebih kurang USD 0.0001 setiap scan (~USD 1 bagi 10,000 scan).
Penggunaan boleh dipantau di openrouter.ai → Activity.
