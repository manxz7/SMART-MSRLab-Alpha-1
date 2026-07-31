"""Try to bypass Lotus's delivery popup and load products"""
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(
        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    )

    # Step 1: Load the page
    page.goto("https://www.lotuss.com.my/en/category/food", timeout=30000)

    # Step 2: Set localStorage to simulate "delivery to KL" already chosen
    page.evaluate("""() => {
        localStorage.setItem('STORE_ID', '1');
        localStorage.setItem('DELIVERY_TYPE', 'delivery');
        localStorage.setItem('POSTCODE', '50450');
        localStorage.setItem('addressId', '1');
    }""")

    # Step 3: Reload with those values set
    page.reload()
    page.wait_for_timeout(6000)

    print("Title:", page.title())
    print("URL:", page.url)

    # Check images
    imgs = page.query_selector_all("img")
    print(f"\nImages found: {len(imgs)}")
    product_imgs = []
    for img in imgs:
        src = img.get_attribute("src") or ""
        alt = img.get_attribute("alt") or ""
        # Product images usually have meaningful alt text and don't come from adobeaem (UI assets)
        if len(alt) > 5 and "adobeaem" not in src and src.startswith("http"):
            product_imgs.append({"alt": alt, "src": src})
            print(f"  PRODUCT? alt='{alt[:50]}' src='{src[:80]}'")

    print(f"\nPotential product images: {len(product_imgs)}")

    # Also check for any API calls made by the page
    html = page.content()
    # Look for product data patterns in the HTML
    import re
    product_names = re.findall(r'"name"\s*:\s*"([^"]{5,50})"', html)
    print(f"\nProduct names found in HTML: {len(product_names)}")
    for name in product_names[:20]:
        print(f"  {name}")

    browser.close()
