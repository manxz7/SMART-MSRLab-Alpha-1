"""Quick inspection of Lotus's website structure"""
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    print("Loading Lotus Malaysia food page...")
    page.goto("https://www.lotuss.com.my/en/category/food", timeout=30000)
    page.wait_for_timeout(5000)  # wait for JS to load products

    print("Title:", page.title())
    print("URL:", page.url)

    # Try common product card selectors
    selectors = [
        "div[class*='product']",
        "div[class*='item']",
        "article",
        "li[class*='product']",
        "[data-testid*='product']",
        "div[class*='card']",
        "div[class*='tile']",
        "div[class*='grid'] > div",
    ]
    print("\n--- Selector search ---")
    for sel in selectors:
        try:
            count = len(page.query_selector_all(sel))
            if count > 0:
                print(f"  FOUND {count:>4} | {sel}")
        except:
            pass

    # Try to find product images
    imgs = page.query_selector_all("img")
    print(f"\n--- Images on page: {len(imgs)} ---")
    for img in imgs[:15]:
        src = img.get_attribute("src") or ""
        alt = img.get_attribute("alt") or ""
        if src and ("product" in src.lower() or "item" in src.lower() or len(alt) > 3):
            print(f"  src: {src[:80]}")
            print(f"  alt: {alt[:50]}")
            print()

    # Save raw HTML snippet for manual inspection
    html = page.content()
    with open("lotus_inspect.html", "w", encoding="utf-8") as f:
        f.write(html)
    print("\nFull HTML saved to lotus_inspect.html")
    print(f"HTML size: {len(html)} chars")

    browser.close()
