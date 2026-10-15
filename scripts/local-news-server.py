#!/usr/bin/env python3
"""Loopback-only mediator fixture for real Ktor engine feature checks."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
from urllib.parse import parse_qs, urlsplit


class LocalNewsServer:
    def __init__(self):
        self.started = threading.Event()
        self.release = threading.Event()
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_GET(self):
                request = urlsplit(self.path)
                if request.path.endswith("/started"):
                    self.respond(200, "started" if owner.started.is_set() else "pending", "text/plain")
                    return
                if self.headers.get("apikey") != "sb_publishable_fixture":
                    self.respond(403, '{"status":"error"}')
                    return
                parameters = parse_qs(request.query)
                query = parameters.get("q", [""])[0]
                if query == "cancel":
                    owner.started.set()
                    owner.release.wait(30)
                    self.respond(200, '{"status":"success","totalResults":0,"results":[]}')
                elif query == "disconnect":
                    self.connection.close()
                elif query == "invalid":
                    self.respond(200, "invalid-json")
                elif query == "failure":
                    self.respond(503, '{"status":"error"}')
                elif query == "🌍 local" and parameters.get("page") == ["opaque+cursor&one"]:
                    self.respond(200, '{"status":"success","totalResults":1,"results":'
                                      '[{"article_id":"engine-fixture","title":"Local"}]}')
                else:
                    self.respond(400, '{"status":"error"}')

            def respond(self, status, body, content_type="application/json"):
                data = body.encode("utf-8")
                try:
                    self.send_response(status)
                    self.send_header("Content-Type", content_type)
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.daemon_threads = True
        self.port = self.server.server_port
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *_):
        self.release.set()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()


if __name__ == "__main__":
    with LocalNewsServer() as fixture:
        print(fixture.port, flush=True)
        threading.Event().wait()
