"""
Interact with Lotus's delivery popup to unlock product listings
"""
from playwright.sync_api import sync_playwright
import time

with sync_playwright() as p:
    # headless=False so we can see what happens (for debugging)
    browser = p.chromium.launch(headless=False, slow_mo=500)
    page = browser.new_page(
        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    )

    print("Opening Lotus's...")
    page.goto("https://www.lotuss.com.my/en/category/food", timeout=30000)
    page.wait_for_timeout(3000)

    # Click "Choose Delivery Method"
    try:
        page.click("#select-delivery-method", timeout=5000)
        print("Clicked delivery method button")
        page.wait_for_timeout(2000)
    except:
        print("Could not click delivery method — trying alternative...")

    # Look for "Delivery" option
    try:
        page.click("text=Delivery", timeout=5000)
        print("Clicked Delivery option")
        page.wait_for_timeout(2000)
    except:
        print("No 'Delivery' text found")

    # Look for postcode input
    try:
        page.fill("input[placeholder*='postcode']", "50450", timeout=5000)
        page.fill("input[placeholder*='Postcode']", "50450", timeout=5000)
        print("Filled postcode: 50450")
        page.wait_for_timeout(1000)
        page.keyboard.press("Enter")
        page.wait_for_timeout(3000)
    except:
        print("No postcode field found")

    # Take screenshot to see current state
    page.screenshot(path="lotus_state.png")
    print("Screenshot saved: lotus_state.png")

    # Check what's on the page now
    imgs = page.query_selector_all("img")
    print(f"\nImages on page: {len(imgs)}")
    for img in imgs[:20]:
        src = img.get_attribute("src") or ""
        alt = img.get_attribute("alt") or ""
        if len(alt) > 5 and "adobeaem" not in src:
            print(f"  alt='{alt[:50]}' | src='{src[:80]}'")

    page.wait_for_timeout(3000)
    browser.close()
