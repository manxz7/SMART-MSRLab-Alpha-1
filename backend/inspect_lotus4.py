"""Close Lotus's login popup and check if products load without login"""
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=300)
    page = browser.new_page(
        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    )

    print("Opening Lotus's...")
    page.goto("https://www.lotuss.com.my/en/category/food", timeout=30000)
    page.wait_for_timeout(2000)

    # Click delivery method to trigger popup
    try:
        page.click("#select-delivery-method", timeout=5000)
        page.wait_for_timeout(2000)
    except:
        pass

    # Close the login popup by clicking the X button
    try:
        # Try various close button selectors
        for sel in ["button[aria-label='close']", "button.close", "[class*='close']", "svg[data-testid='CloseIcon']", "button:has(svg)"]:
            btns = page.query_selector_all(sel)
            if btns:
                btns[0].click()
                print(f"Closed popup with: {sel}")
                page.wait_for_timeout(2000)
                break
    except Exception as e:
        print(f"Close attempt: {e}")

    # Try pressing Escape to close any modal
    page.keyboard.press("Escape")
    page.wait_for_timeout(3000)

    # Screenshot to see state
    page.screenshot(path="lotus_after_close.png")
    print("Screenshot: lotus_after_close.png")

    # Check for products now
    imgs = page.query_selector_all("img")
    print(f"Images: {len(imgs)}")
    for img in imgs:
        src = img.get_attribute("src") or ""
        alt = img.get_attribute("alt") or ""
        if len(alt) > 5 and "adobeaem" not in src and src.startswith("http"):
            print(f"  PRODUCT: alt='{alt[:60]}' src='{src[:80]}'")

    page.wait_for_timeout(3000)
    browser.close()
