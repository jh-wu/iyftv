#!/usr/bin/env python3
"""Follows one title from search to its first video segment, the way the app does.

Usage: probe_play.py <search text>. Prints each step to the CI log so a
playback failure on a TV can be traced to the response that causes it.
"""
import hashlib, json, re, sys, urllib.parse, urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
WEB = "https://www.iyf.tv"
API = "https://m10.iyf.tv"
HEADERS = {"User-Agent": UA, "Referer": WEB + "/", "Origin": WEB}


def fetch(url, headers=HEADERS, limit=None):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, dict(r.headers), r.read(limit) if limit else r.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read(2000)
    except Exception as e:
        return 0, {}, repr(e).encode()


def text(b):
    return b.decode("utf-8", "replace")


_, _, home = fetch(WEB + "/")
pconf = json.loads(re.search(r'"pConfig"\s*:\s*(\{[^}]*\})', text(home)).group(1))
pub, priv = pconf["publicKey"], pconf["privateKey"][0]


def api(path, query):
    decoded = "&".join(
        f"{k}={urllib.parse.unquote(v)}" for k, _, v in (p.partition("=") for p in query.split("&"))
    )
    vv = hashlib.md5(f"{pub}&{decoded.lower()}&{priv}".encode()).hexdigest()
    code, _, body = fetch(f"{API}{path}?{query}&vv={vv}&pub={pub}")
    print(f"\n===== {path} HTTP {code}")
    return json.loads(body)


def section(t):
    print(f"\n===== {t}", flush=True)


title = sys.argv[1] if len(sys.argv) > 1 else "沉默的证人"
res = api("/v3/list/briefsearch", "tags=" + urllib.parse.quote(title) + "&orderby=4&page=1&size=36&desc=1&isserial=-1")
items = res["data"]["info"][0]["result"]
for it in items[:5]:
    print(json.dumps({k: it.get(k) for k in ("title", "contxt", "key", "atypeName", "isSerial")}, ensure_ascii=False))
vid = items[0].get("contxt") or items[0].get("key")

detail = api("/v3/video/detail", f"cinema=1&device=1&player=CkPlayer&tech=HLS&country=HU&lang=cns&v=1&id={vid}&region=GL.")
d = detail["data"]["info"][0]
print(json.dumps({k: d.get(k) for k in ("title", "key", "videoType", "isSerial", "isVip", "isFree", "lastName", "cid", "charge")}, ensure_ascii=False))
playlist = d.get("playList") or []
print("playList from detail:", json.dumps(playlist[:3], ensure_ascii=False), "count", len(playlist))
lp = api("/v3/video/languagesplaylist", f"cinema=1&vid={vid}&lsk=1&taxis=0&cid=0,1,4,133")
lpl = (lp.get("data", {}).get("info") or [{}])[0].get("playList") or []
print("languagesplaylist:", json.dumps(lpl[:3], ensure_ascii=False), "count", len(lpl))
ep = (playlist or lpl)[0]["key"]

play = api("/v3/video/play", f"cinema=1&id={ep}&a=0&lang=none&usersign=1&region=GL.&device=1&isMasterSupport=1")
print(json.dumps(play, ensure_ascii=False)[:3000])

urls = []
def walk(o):
    if isinstance(o, dict):
        if o.get("isHls") is True and isinstance(o.get("result"), str):
            urls.append(o["result"])
        for v in o.values():
            walk(v)
    elif isinstance(o, list):
        for v in o:
            walk(v)
walk(play)
section(f"HLS entries: {urls}")


def follow(url, depth=0):
    code, hdrs, body = fetch(url)
    section(f"playlist {url[:120]} -> HTTP {code} {hdrs.get('Content-Type')} {len(body)} bytes")
    lines = text(body).splitlines()
    print("\n".join(lines[:25]))
    refs = [l for l in lines if l and not l.startswith("#")]
    keys = re.findall(r'URI="([^"]+)"', text(body))
    for k in keys[:1]:
        kc, kh, kb = fetch(urllib.parse.urljoin(url, k))
        print(f"key {k[:80]} -> HTTP {kc} {len(kb)} bytes")
    if not refs:
        return
    nxt = urllib.parse.urljoin(url, refs[0])
    if ".m3u8" in refs[0] and depth < 3:
        follow(nxt, depth + 1)
        return
    for label, h in [("with headers", HEADERS), ("plain UA", {"User-Agent": UA})]:
        c, sh, sb = fetch(nxt, h, limit=64)
        print(f"segment {nxt[:120]} ({label}) -> HTTP {c} {sh.get('Content-Type')} len={sh.get('Content-Length')} first bytes {sb[:16].hex()}")


for u in urls[:2]:
    follow(u)

section("how the web client builds the play request")
_, _, homepage = fetch(WEB + "/")
h = text(homepage)
for k in ["region", "country", "ipAddress", "clientIp", "\"ip\"", "geo", "area"]:
    for m in list(re.finditer(k, h))[:3]:
        print(f"home {k}:", h[max(0, m.start() - 150):m.start() + 200].replace("\n", " "))
scripts = re.findall(r'<script[^>]*src="([^"]+\.js)"', h)
print("scripts:", scripts)
for src in scripts:
    _, _, js = fetch(urllib.parse.urljoin(WEB + "/", src))
    js = text(js)
    for k in ["video/play", "usersign", "isMasterSupport", "region=", "region:", "vCustomParameter"]:
        for m in list(re.finditer(re.escape(k), js))[:3]:
            print(f"--- {src} {k}:", js[max(0, m.start() - 400):m.start() + 300].replace("\n", " "))
