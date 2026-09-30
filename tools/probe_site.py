#!/usr/bin/env python3
"""Probes iyf.tv so the Android client can be matched to the real site.

Prints (to the CI log) how the web app signs API calls and what the
responses look like, and saves raw responses to the output directory.
"""
import hashlib, json, os, re, sys, urllib.parse, urllib.request

OUT = sys.argv[1] if len(sys.argv) > 1 else "probe-out"
os.makedirs(OUT, exist_ok=True)
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
WEB = "https://www.iyf.tv"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": WEB + "/", "Origin": WEB})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, repr(e)


def save(name, body):
    with open(os.path.join(OUT, name), "w") as f:
        f.write(body)


def section(t):
    print(f"\n===== {t} =====", flush=True)


# 1. Homepage config
code, home = get(WEB + "/")
save("home.html", home)
section(f"homepage HTTP {code}")
m = re.search(r'"pConfig"\s*:\s*(\{[^}]*\})', home)
pconf = json.loads(m.group(1)) if m else {}
print("pConfig:", pconf)
for k in ["apiVersion", "apiHost", "apiUrl", "baseUrl", "host"]:
    for mm in re.finditer(r'"%s"\s*:\s*"([^"]*)"' % k, home):
        print(k, "=", mm.group(1))
pub = pconf.get("publicKey", "")
privs = pconf.get("privateKey", [])

# 2. Main bundle: find signing and endpoints
scripts = re.findall(r'<script[^>]*src="(/app/main\.[^"]+\.js)"', home)
bundle = ""
if scripts:
    code, bundle = get(WEB + scripts[0])
    save("main.js", bundle)
    section(f"main bundle {scripts[0]} HTTP {code}, {len(bundle)} bytes")
    for pat in []:
        for mm in list(re.finditer(pat, bundle))[:4]:
            s = max(0, mm.start() - 400)
            print(f"--- {pat} @ {mm.start()}:\n{bundle[s:mm.start() + 400]}\n")
    for pat in [r"m10\.", r"iyf\.tv", r"apiHost", r"ApiHost", r"apiUrl", r"\.api", r"getHost|GetHost"]:
        hits = [mm.start() for mm in re.finditer(pat, bundle)]
        print(f"--- bundle {pat}: {len(hits)} hits")
        for h in hits[:3]:
            print("   ", bundle[max(0, h - 250):h + 250].replace("\n", " "))
    for pat in [r"m10", r"api[A-Za-z]*\"\s*:"]:
        for mm in list(re.finditer(pat, home))[:5]:
            print(f"--- home {pat}:", home[max(0, mm.start() - 200):mm.start() + 200].replace("\n", " "))
    hosts = sorted(set(re.findall(r'https?://[a-z0-9.-]+\.(?:iyf|yfsp)[a-z0-9.-]*', bundle)))
    print("hosts in bundle:", hosts)
    paths = sorted(set(re.findall(r'["\'`](/?(?:api|v\d)/[A-Za-z0-9/_-]+)', bundle)))
    print("api paths in bundle:", paths)


def md5(s):
    return hashlib.md5(s.encode()).hexdigest()


# 3. Try candidate signing schemes on a list call
API = "https://m10.iyf.tv"
q = "cinema=1&page=1&size=36&orderby=0&desc=1&cid=0,1,4&isserial=-1&isIndex=-1&isfree=-1"
section("signing candidates on /api/list/Search")
priv = privs[0] if privs else ""
cands = {
    "pub&lq&priv": md5(f"{pub}&{q.lower()}&{priv}"),
    "pub&q&priv": md5(f"{pub}&{q}&{priv}"),
    "priv&lq&pub": md5(f"{priv}&{q.lower()}&{pub}"),
    "lq&priv": md5(f"{q.lower()}&{priv}"),
}
for name, vv in cands.items():
    code, body = get(f"{API}/api/list/Search?{q}&vv={vv}&pub={pub}")
    print(name, code, body[:160])
    save(f"list_{name.replace('&', '_')}.json", body)

# 3b. Category ids and list item fields
section("categories")
for n in range(2, 12):
    cq = f"cinema=1&page=1&size=2&orderby=0&desc=1&cid=0,1,{n}&isserial=-1&isIndex=-1&isfree=-1"
    code, body = get(f"{API}/api/list/Search?{cq}&vv={md5(f'{pub}&{cq.lower()}&{priv}')}&pub={pub}")
    try:
        info = json.loads(body)["data"]["info"][0]
        items = info["result"]
        print(f"cid 0,1,{n}: count={info.get('recordcount')} type={items[0].get('atypeName') if items else None}")
        if n == 4 and items:
            print("list item keys:", sorted(items[0].keys()))
            print("list item:", json.dumps(items[0], ensure_ascii=False)[:800])
    except Exception as e:
        print(f"cid 0,1,{n}: parse failed {e} {body[:120]}")

# 4. Search (worked unsigned-ish before) and field names
section("search")
sq = "tags=" + urllib.parse.quote("繁花") + "&orderby=4&page=1&size=10&desc=1&isserial=-1"
code, body = get(f"{API}/v3/list/briefsearch?{sq}&vv={cands['pub&lq&priv']}&pub={pub}")
save("search.json", body)
first = None
try:
    first = json.loads(body)["data"]["info"][0]["result"][0]
    print("result keys:", sorted(first.keys()))
    print("first:", json.dumps({k: first[k] for k in first if k in ('title', 'contxt', 'id', 'key', 'mediaKey', 'imgPath', 'lastName', 'cidMapper')}, ensure_ascii=False))
except Exception as e:
    print("parse failed", e, body[:300])

# 5. Detail / playlist / play for that title using the contxt/key candidates
if first:
    for field in ("key", "mediaKey", "contxt"):
        vid = first.get(field)
        if not vid:
            continue
        for path, qq in [
            ("/v3/video/detail", f"cinema=1&device=1&player=CkPlayer&tech=HLS&country=HU&lang=cns&v=1&id={vid}&region=GL."),
            ("/v3/video/languagesplaylist", f"cinema=1&vid={vid}&lsk=1&taxis=0&cid=0,1,4,133"),
        ]:
            vv = md5(f"{pub}&{qq.lower()}&{priv}")
            code, body = get(f"{API}{path}?{qq}&vv={vv}&pub={pub}")
            section(f"{path} with {field}={vid}: HTTP {code}")
            print(body[:1500])
            save(f"{path.split('/')[-1]}_{field}.json", body)

    # Play API for the first episode
    try:
        ep = json.loads(open(os.path.join(OUT, "languagesplaylist_contxt.json")).read())["data"]["info"][0]["playList"][0]["key"]
    except Exception as e:
        ep = None
        print("no episode key", e)
    if ep:
        for qq in [
            f"cinema=1&id={ep}&a=0&lang=none&usersign=1&region=GL.&device=1&isMasterSupport=1",
            f"cinema=1&id={ep}&a=0&lang=none&usersign=1&region=GL.&device=0&isMasterSupport=1",
        ]:
            vv = md5(f"{pub}&{qq.lower()}&{priv}")
            code, body = get(f"{API}/v3/video/play?{qq}&vv={vv}&pub={pub}")
            section(f"/v3/video/play {qq}: HTTP {code}")
            print(body[:2500])
            save("play.json", body)

    # Watch page HTML for the title
    vid = first.get("contxt") or first.get("key")
    code, body = get(f"{WEB}/play/{vid}")
    save("playpage.html", body)
    section(f"play page /play/{vid}: HTTP {code}, {len(body)} bytes")
    print(re.findall(r'https?://[^"\'\s]+\.m3u8[^"\'\s]*', body)[:5])
