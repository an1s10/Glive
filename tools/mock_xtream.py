"""Faux serveur Xtream Codes pour tester Glive sur émulateur.
Catalogue volumineux, doublons volontaires, EPG autour de l'heure actuelle."""
import base64, json, os, random, struct, sys, time, zlib
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

random.seed(42)
HOST = sys.argv[1] if len(sys.argv) > 1 else "http://10.0.2.2:8000"
VIDEO_TS = os.environ.get("VIDEO_TS", "test.ts")
VIDEO_MP4 = os.environ.get("VIDEO_MP4", "test.mp4")

def png(r, g, b, w=64, h=64):
    raw = b"".join(b"\x00" + bytes([r, g, b]) * w for _ in range(h))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")

PREFIX = ["FR", "AR", "UK", "ES", "IT", "CA", "DZ", "TR", "DE", "PT"]
live_cats, channels = [], []
cid = 1
for i in range(120):
    p = PREFIX[i % len(PREFIX)]
    live_cats.append({"category_id": str(i + 1), "category_name": f"{p}| Groupe {i + 1}", "parent_id": 0})
live_cats.append(dict(live_cats[0]))  # doublon volontaire
for n in range(4000):
    cat = live_cats[n % 120]
    p = cat["category_name"][:2]
    channels.append({"num": n + 1, "name": f"{p}| Chaîne {n + 1} HD", "stream_type": "live", "stream_id": n + 1,
                     "stream_icon": f"{HOST}/img/{n % 50}.png" if n % 3 else "", "epg_channel_id": f"ch{n}",
                     "category_id": cat["category_id"], "tv_archive": 1 if n % 4 == 0 else 0, "tv_archive_duration": 3 if n % 4 == 0 else 0})
channels += [dict(channels[i]) for i in range(0, 200, 7)]  # doublons

vod_cats = [{"category_id": str(500 + i), "category_name": f"{PREFIX[i % 4]}| Films {i}", "parent_id": 0} for i in range(60)]
movies = [{"num": i, "name": f"Film {i} ({1990 + i % 35})", "stream_type": "movie", "stream_id": 10000 + i,
           "stream_icon": f"{HOST}/img/{i % 50}.png", "rating": f"{(i % 90) / 10 + 1:.1f}", "rating_5based": 3.4,
           "added": str(1700000000 + i * 60), "category_id": vod_cats[i % 60]["category_id"], "container_extension": "mp4"}
          for i in range(25000)]
movies += [dict(movies[i]) for i in range(0, 500, 9)]
ser_cats = [{"category_id": str(900 + i), "category_name": f"{PREFIX[i % 4]}| Séries {i}", "parent_id": 0} for i in range(30)]
series = [{"num": i, "name": f"Série {i}", "series_id": 50000 + i, "cover": f"{HOST}/img/{i % 50}.png", "plot": "Une série de test.",
           "releaseDate": "2020-01-01", "rating": "7.5", "last_modified": str(1700000000 + i), "category_id": ser_cats[i % 30]["category_id"]}
          for i in range(6000)]

def b64(s):
    return base64.b64encode(s.encode()).decode()

def epg(sid, full):
    now = int(time.time())
    start = now - (now % 3600) - (6 * 3600 if full else 0)
    out = []
    for k in range(30 if full else 4):
        s = start + k * 3600
        out.append({"id": str(k), "title": b64(f"Émission {k} de la chaîne {sid}"), "description": b64("Description de test."),
                    "start": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(s)), "end": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(s + 3600)),
                    "start_timestamp": str(s), "stop_timestamp": str(s + 3600), "has_archive": 1 if (full and s + 3600 < now and sid % 4 == 1) else 0})
    if full:
        out.append(dict(out[3]))  # doublon de début
    return {"epg_listings": out}

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send_json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_file(self, path, ctype):
        size = os.path.getsize(path)
        rng = self.headers.get("Range")
        start, end = 0, size - 1
        if rng and rng.startswith("bytes="):
            a, _, b = rng[6:].partition("-")
            start = int(a or 0); end = int(b) if b else size - 1
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        with open(path, "rb") as f:
            f.seek(start)
            left = end - start + 1
            while left > 0:
                chunk = f.read(min(65536, left))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return
                left -= len(chunk)

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        if u.path == "/player_api.php":
            a = q.get("action")
            if a is None:
                return self.send_json({"user_info": {"auth": 1, "status": "Active", "max_connections": "1"}, "server_info": {"timezone": "America/Toronto"}})
            sid = int(q.get("stream_id", "0") or 0)
            table = {
                "get_live_categories": lambda: live_cats, "get_live_streams": lambda: channels,
                "get_vod_categories": lambda: vod_cats, "get_vod_streams": lambda: movies,
                "get_series_categories": lambda: ser_cats, "get_series": lambda: series,
                "get_short_epg": lambda: epg(sid, False), "get_simple_data_table": lambda: epg(sid, True),
                "get_vod_info": lambda: {"info": {"plot": "Un film de test.", "genre": "Action", "duration": "01:52:00", "rating": "7.2", "backdrop_path": [f"{HOST}/img/7.png"], "releasedate": "2021-05-01"}},
                "get_series_info": lambda: {"info": {"plot": "Série de test", "rating": "8"},
                                            "episodes": {str(s): [{"id": str(s * 100 + e), "episode_num": e, "title": f"Épisode {e}", "container_extension": "mp4", "info": {"duration": "00:45:00"}} for e in range(1, 9)] for s in range(1, 4)}},
            }
            return self.send_json(table.get(a, lambda: [])())
        if u.path.startswith("/img/"):
            n = int(u.path.split("/")[-1].split(".")[0])
            body = png((n * 37) % 255, (n * 71) % 255, (n * 113) % 255)
            self.send_response(200); self.send_header("Content-Type", "image/png"); self.send_header("Content-Length", str(len(body))); self.end_headers()
            return self.wfile.write(body)
        if u.path.startswith(("/live/", "/timeshift/")):
            return self.send_file(VIDEO_TS, "video/mp2t")
        if u.path.startswith(("/movie/", "/series/")):
            return self.send_file(VIDEO_MP4, "video/mp4")
        self.send_response(404); self.end_headers()

if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), H).serve_forever()
