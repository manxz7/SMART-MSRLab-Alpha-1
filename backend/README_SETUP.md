# Halal Product Checker API — Setup Guide

## Requirements
- Docker & Docker Compose installed
- Port 8000 (API) and 5432 (PostgreSQL) exposed

## Files
| File | Purpose |
|---|---|
| `api.py` | FastAPI backend |
| `Dockerfile` | Docker image config |
| `docker-compose.yml` | Multi-container setup |
| `requirements.txt` | Python dependencies |
| `init.sql` | Database schema + index setup |
| `halal_data_cleaned.csv` | 348K JAKIM halal records |

## Deploy

```bash
# 1. Create project folder
mkdir halal-app && cd halal-app

# 2. Copy all files into this folder

# 3. Build and start
docker compose up -d

# 4. Check logs
docker compose logs -f api
```

## Verify
- API docs: http://[server-ip]:8000/docs
- Health: http://[server-ip]:8000/

## Notes
- Database auto-imports CSV on first start (takes ~2-3 minutes)
- Vision AI uses external LM Studio at https://lm-studio.careerlab.my
- API key: sk-iC0mk0nZQtB1IAV0Cbri1g
