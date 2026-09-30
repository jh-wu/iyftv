#!/usr/bin/env bash
# Fetches the iyf.tv homepage and a few API calls, signed the way the app signs
# them, and saves the raw responses. Used to check IyfConfig/IyfSigner.
set -u
out=${1:-probe-out}
mkdir -p "$out"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
WEB=https://www.iyf.tv
API=https://m10.iyf.tv

fetch() { curl -sS -L -A "$UA" -e "$WEB/" -H "Origin: $WEB" --max-time 30 -w '\nHTTP %{http_code}\n' "$@"; }

fetch "$WEB/" > "$out/home.html"
echo "== homepage: $(tail -1 "$out/home.html"), $(wc -c < "$out/home.html") bytes"
grep -o 'https\?://[a-z0-9.-]*iyf[a-z0-9.-]*' "$out/home.html" | sort | uniq -c | sort -rn | head -20
grep -o '<script[^>]*src="[^"]*"' "$out/home.html" | head -20

pub=$(grep -o '"publicKey"[[:space:]]*:[[:space:]]*"[^"]*"' "$out/home.html" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
priv=$(grep -o '"privateKey"[[:space:]]*:[[:space:]]*\[[^]]*\]' "$out/home.html" | head -1 | grep -o '"[^"]*"' | tail -n +2 | tr -d '"' | head -1)
echo "== publicKey: ${pub:-<not found>}  first privateKey: ${priv:+found}"
grep -o '.\{200\}publicKey.\{400\}' "$out/home.html" | head -2

sign() { local q=$1; local lq; lq=$(echo -n "$q" | tr 'A-Z' 'a-z'); echo -n "$q&vv=$(echo -n "$pub&$lq&$priv" | md5sum | cut -d' ' -f1)&pub=$pub"; }

call() {
  local name=$1 path=$2 query=$3
  fetch "$API$path?$(sign "$query")" > "$out/$name.json"
  echo "== $name: $(tail -1 "$out/$name.json")"
  head -c 1500 "$out/$name.json"; echo
}

call list /api/list/Search "cinema=1&page=1&size=36&orderby=0&desc=1&cid=0,1,4&isserial=-1&isIndex=-1&isfree=-1"
call search /v3/list/briefsearch "tags=%E7%B9%81%E8%8A%B1&orderby=4&page=1&size=36&desc=1&isserial=-1"
exit 0
