#!/usr/bin/env python3
"""Tiny local test server for load-resilience-tester demos.

Serves 200 OK with a small JSON body on every path, counts requests, and
prints a one-line log per request. Listens on 127.0.0.1 only -- it is not
reachable from other machines. Stdlib only.

Usage:  python3 demo/server.py [--port 8765]
"""

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    count = 0

    def do_GET(self):
        Handler.count += 1
        body = json.dumps({"ok": True, "requests_served": Handler.count}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        print(f"[{Handler.count}] GET {self.path} -> 200", flush=True)

    def log_message(self, *args):
        pass  # keep stdout to the one-line log above


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Local demo server (127.0.0.1 only).")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Demo server listening on http://127.0.0.1:{args.port}/ (Ctrl+C to stop)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print(f"\nStopped. Served {Handler.count} requests.", flush=True)
