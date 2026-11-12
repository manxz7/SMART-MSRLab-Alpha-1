import requests, time, sys

img_path = r"lotus_images\100PLUS_ACTIVE_REPLENISH_1L.jpg"
url = "http://localhost:8000/scan-image"

print("Testing scan locally...")
t = time.time()
with open(img_path, "rb") as f:
    resp = requests.post(url, files={"file": ("test.jpg", f, "image/jpeg")}, timeout=120)
elapsed = time.time() - t
print(f"Status: {resp.status_code}  Time: {elapsed:.1f}s")
print(resp.text[:500])
