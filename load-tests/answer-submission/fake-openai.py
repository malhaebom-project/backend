import argparse
import json
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class RequestMetrics:
    def __init__(self):
        self._lock = threading.Lock()
        self.active = 0
        self.max_active = 0
        self.total = 0
        self.completed = 0
        self.disconnected = 0

    def start(self):
        with self._lock:
            self.active += 1
            self.total += 1
            self.max_active = max(self.max_active, self.active)

    def finish(self, disconnected=False):
        with self._lock:
            self.active -= 1
            if disconnected:
                self.disconnected += 1
            else:
                self.completed += 1

    def snapshot(self):
        with self._lock:
            return {
                "active": self.active,
                "maxActive": self.max_active,
                "total": self.total,
                "completed": self.completed,
                "disconnected": self.disconnected,
            }


class LoadTestHttpServer(ThreadingHTTPServer):
    request_queue_size = 1024
    daemon_threads = True


class FakeOpenAiHandler(BaseHTTPRequestHandler):
    server_version = "MalhaebomFakeOpenAI/1.0"

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"status": "UP"})
            return
        if self.path == "/metrics":
            self._send_json(200, self.server.metrics.snapshot())
            return
        self._send_json(404, {"error": "not_found"})

    def do_POST(self):
        if not self.path.endswith("/chat/completions"):
            self._send_json(404, {"error": "not_found"})
            return

        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length)
        try:
            request = json.loads(raw_body or b"{}")
        except json.JSONDecodeError:
            self._send_json(400, {"error": "invalid_json"})
            return

        self.server.metrics.start()
        time.sleep(self.server.delay_seconds)
        response = {
            "id": f"chatcmpl-loadtest-{uuid.uuid4()}",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": request.get("model", "load-test-model"),
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": json.dumps(
                            {
                                "recognized": True,
                                "meaningScore": 50,
                                "expressionScore": 30,
                                "grammarScore": 20,
                                "feedbackText": "현재진행형을 정확하게 사용했어요!",
                            },
                            ensure_ascii=False,
                        ),
                    },
                    "finish_reason": "stop",
                }
            ],
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 30,
                "total_tokens": 130,
            },
        }
        disconnected = not self._send_json(200, response)
        self.server.metrics.finish(disconnected=disconnected)

    def _send_json(self, status, payload):
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)
            return True
        except (BrokenPipeError, ConnectionResetError):
            return False

    def log_message(self, format_string, *args):
        return


def main():
    parser = argparse.ArgumentParser(
        description="Delayed OpenAI-compatible server for load tests."
    )
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--delay-seconds", type=float, default=5.0)
    args = parser.parse_args()

    server = LoadTestHttpServer((args.host, args.port), FakeOpenAiHandler)
    server.delay_seconds = args.delay_seconds
    server.metrics = RequestMetrics()
    print(
        f"fake OpenAI listening on http://{args.host}:{args.port} "
        f"delay={args.delay_seconds}s",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
