import time
from openai import OpenAI

client = OpenAI(
    base_url="https://lm-studio.careerlab.my/v1",
    api_key="sk-iC0mk0nZQtB1IAV0Cbri1g",
    default_headers={"User-Agent": "curl/8.6.0"}
)

t = time.time()
r = client.chat.completions.create(
    model="glm-4.7-flash",
    messages=[{
        "role": "user",
        "content": (
            "category: Produk\n"
            "company: NESTLE\n"
            "brand: MILO\n"
            "product_name: MILO COKLAT\n"
            "address: \n"
            'Return JSON: {"company_ner":"NESTLE","brand_ner":"MILO",'
            '"product_ner":"MILO COKLAT","address_ner":"","confidence":0.95}'
        )
    }],
    temperature=0.1,
    max_tokens=1024,
)

elapsed = time.time() - t
choice = r.choices[0]
print(f"Time         : {elapsed:.1f}s")
print(f"Finish reason: {choice.finish_reason}")
print(f"Content      : {choice.message.content}")
rc = getattr(choice.message, "reasoning_content", None)
print(f"Think chars  : {len(rc) if rc else 0}")
