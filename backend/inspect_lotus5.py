"""
Inspect Lotus's product structure on a category page
to understand the HTML structure for scraping
"""
from playwright.sync_api import sync_playwright
import json

def close_popups(page):
    """Close any popups that appear"""
    # Close login popup if shown
    try:
        page.click("button:has(svg)", timeout=3000)
    except:
        pass
    page.keyboard.press("Escape")
    page.wait_for_timeout(500)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=200)
    page = browser.new_page(
        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    )

    print("Opening Lotus's homepage to close popup first...")
    page.goto("https://www.lotuss.com.my/en", timeout=30000)
    page.wait_for_timeout(2000)

    # Click the delivery selector
    try:
        page.click("#select-delivery-method", timeout=5000)
        page.wait_for_timeout(2000)
        close_popups(page)
        page.wait_for_timeout(2000)
    except:
        pass

    # Now navigate to Grocery category
    print("Navigating to Grocery category...")
    page.goto("https://www.lotuss.com.my/en/category/grocery", timeout=30000)
    page.wait_for_timeout(3000)
    
    # Scroll down to trigger lazy loading
    for i in range(3):
        page.mouse.wheel(0, 1000)
        page.wait_for_timeout(1000)

    page.screenshot(path="lotus_grocery.png")
    print("Screenshot: lotus_grocery.png")

    # Find product cards
    imgs = page.query_selector_all("img")
    print(f"\nTotal images: {len(imgs)}")
    
    product_imgs = []
    for img in imgs:
        src = img.get_attribute("src") or img.get_attribute("data-src") or ""
        alt = img.get_attribute("alt") or ""
        if (len(alt) > 3 and 
            "logo" not in alt.lower() and 
            "banner" not in alt.lower() and 
            src.startswith("http")):
            product_imgs.append({"alt": alt, "src": src})
            
    print(f"Product candidate images: {len(product_imgs)}")
    for p_img in product_imgs[:20]:
        print(f"  '{p_img['alt'][:50]}' -> {p_img['src'][:80]}")

    # Look for product name elements
    print("\n--- Checking product title elements ---")
    for sel in ["[class*='product-name']", "[class*='ProductName']", "[class*='product_name']", 
                "h3", "[class*='title']", "[class*='Name']"]:
        els = page.query_selector_all(sel)
        if els and len(els) > 2:
            print(f"Selector '{sel}': {len(els)} elements")
            for el in els[:3]:
                print(f"  text: '{el.inner_text()[:60]}'")

    # Get full page HTML snippet around first product
    print("\n--- Page URL ---")
    print(page.url)

    page.wait_for_timeout(3000)
    browser.close()
