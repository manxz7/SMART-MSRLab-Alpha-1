import requests, time

img_path = r"lotus_images\100PLUS_ACTIVE_REPLENISH_1L.jpg"
url = "https://gratulatorily-transinsular-legend.ngrok-free.dev/scan-image"

print("Testing scan via ngrok...")
t = time.time()
with open(img_path, "rb") as f:
    resp = requests.post(
        url,
        files={"file": ("test.jpg", f, "image/jpeg")},
        headers={"ngrok-skip-browser-warning": "true"},
        timeout=120
    )
elapsed = time.time() - t
print(f"Status: {resp.status_code}  Time: {elapsed:.1f}s")
print(resp.text[:500])
