#!/usr/bin/env python3
"""Shows what iyf.tv offers for scores and list filters, for the app's category tabs."""
import hashlib, json, re, urllib.parse, urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
WEB = "https://www.iyf.tv"
API = "https://m10.iyf.tv"
HEADERS = {"User-Agent": UA, "Referer": WEB + "/", "Origin": WEB}


def fetch(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read(2000).decode("utf-8", "replace")
    except Exception as e:
        return 0, repr(e)


_, home = fetch(WEB + "/")
pconf = json.loads(re.search(r'"pConfig"\s*:\s*(\{[^}]*\})', home).group(1))
pub, priv = pconf["publicKey"], pconf["privateKey"][0]


def api(path, query, show=True):
    decoded = "&".join(f"{k}={urllib.parse.unquote(v)}" for k, _, v in (p.partition("=") for p in query.split("&")))
    vv = hashlib.md5(f"{pub}&{decoded.lower()}&{priv}".encode()).hexdigest()
    code, body = fetch(f"{API}{path}?{query}&vv={vv}&pub={pub}")
    print(f"\n===== {path}?{query} HTTP {code}", flush=True)
    try:
        return json.loads(body)
    except Exception:
        print(body[:500])
        return {}


def scoreish(o, path=""):
    """Prints every field whose name hints at a score, rating, region or language."""
    if isinstance(o, dict):
        for k, v in o.items():
            if re.search(r"score|rate|rating|douban|imdb|region|area|country|lang|year|tag|type", k, re.I) and not isinstance(v, (dict, list)):
                print(f"  {path}.{k} = {json.dumps(v, ensure_ascii=False)[:120]}")
            scoreish(v, f"{path}.{k}")
    elif isinstance(o, list) and o:
        scoreish(o[0], path + "[0]")


lst = api("/api/list/Search", "cinema=1&page=1&size=3&orderby=0&desc=1&cid=0,1,3&isserial=-1&isIndex=-1&isfree=-1")
items = (lst.get("data", {}).get("info") or [{}])[0].get("result") or []
if items:
    print("first list item:", json.dumps(items[0], ensure_ascii=False)[:2500])
scoreish(lst)
if items:
    d = api("/v3/video/detail", f"cinema=1&device=1&player=CkPlayer&tech=HLS&country=HU&lang=cns&v=1&id={items[0]['key']}&region=GL.")
    print("detail:", json.dumps(d, ensure_ascii=False)[:2500])
    scoreish(d)

print("\n===== API names in the web client")
_, page = fetch(WEB + "/list/movie")
scripts = re.findall(r'<script[^>]*src="([^"]+\.js)"', home)
for src in scripts:
    if "main" not in src:
        continue
    _, js = fetch(urllib.parse.urljoin(WEB + "/", src))
    for m in re.finditer(r'"([a-z0-9-]+)"\s*:\s*"(/(?:api|v3)/[^"]+)"', js):
        print(f"  {m.group(1)} -> {m.group(2)}")
    for k in ["list-search", "filter", "Filter", "condition", "getfilter", "orderby:", "isserial", "regionId", "langId"]:
        for m in list(re.finditer(re.escape(k), js))[:4]:
            print(f"--- {k}:", js[max(0, m.start() - 300):m.start() + 400].replace("\n", " "))
print("\n===== list page markup mentioning filters")
for k in ["地区", "语言", "年份", "filter", "condition"]:
    for m in list(re.finditer(k, page))[:3]:
        print(f"page {k}:", page[max(0, m.start() - 200):m.start() + 300].replace("\n", " "))

print("\n===== filter option endpoints")
for path, q in [("/api/list/GetTree", "cid=0,1,3"), ("/v3/list/GetTree", "cid=0,1,3"),
                ("/v3/list/GetSearchCondition", "version=1"), ("/api/list/GetSearchCondition", "version=1")]:
    r = api(path, q)
    print(json.dumps(r, ensure_ascii=False)[:4000])
print("\n===== endpoint bases")
for src in scripts:
    if "main" not in src:
        continue
    _, js = fetch(urllib.parse.urljoin(WEB + "/", src))
    for k in ["APIV3_ENDPOINT:", "API_ENDPOINT:", "APIM10_ENDPOINT:", "APIV3_ENDPOINT=", "API_ENDPOINT="]:
        for m in list(re.finditer(re.escape(k), js))[:2]:
            print(f"--- {k}", js[m.start():m.start() + 200])
    for k in [".rating", ".score", "rating:", "score:"]:
        for m in list(re.finditer(re.escape(k), js))[:3]:
            print(f"--- {k}:", js[max(0, m.start() - 250):m.start() + 250].replace("\n", " "))
print("\n===== filtered list")
api("/api/list/Search", "cinema=1&page=1&size=3&orderby=0&desc=1&cid=0,1,3&isserial=-1&isIndex=-1&isfree=-1&region=日本&language=&year=&label=")
r = api("/api/list/Search", "cinema=1&page=1&size=3&orderby=0&desc=1&cid=0,1,3&isserial=-1&isIndex=-1&isfree=-1&region=%E6%97%A5%E6%9C%AC")
print([ (i.get("title"), i.get("regional")) for i in (r.get("data", {}).get("info") or [{}])[0].get("result") or []])
r = api("/api/list/Search", "cinema=1&page=1&size=3&orderby=0&desc=1&cid=0,1,3&isserial=-1&isIndex=-1&isfree=-1&language=%E8%8B%B1%E8%AF%AD")
print([ (i.get("title"), i.get("lang")) for i in (r.get("data", {}).get("info") or [{}])[0].get("result") or []])
