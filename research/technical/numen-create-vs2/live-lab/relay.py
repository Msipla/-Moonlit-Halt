"""Loopback transport for Numen's native streaming Chat Completions client."""
import argparse
import json
import os
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ENDPOINT = "https://api.deepseek.com/chat/completions"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("port", type=int)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    key = os.environ["DEEPSEEK_API_KEY"]
    trace = args.evidence.open("a", encoding="utf-8")

    class Relay(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def record(self, item):
            trace.write(json.dumps(item, ensure_ascii=False) + "\n")
            trace.flush()

        def do_POST(self):
            if self.path not in ("/chat/completions", "/v1/chat/completions"):
                self.send_error(404)
                return
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            body["thinking"] = {"type": "disabled"}
            body.pop("reasoning_effort", None)
            body["stream"] = True
            body["stream_options"] = {"include_usage": True}
            body["max_tokens"] = 4096
            messages = body.get("messages", [])
            started = time.time()
            event = {"utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(started)),
                     "model": body.get("model"), "content": "", "calls": [],
                     "tools": [tool["function"]["name"] for tool in body.get("tools", [])],
                     "context": [{"role": m.get("role"), "content": m.get("content"),
                                  "tool_call_id": m.get("tool_call_id")}
                                 for m in messages[-4:] if m.get("role") in ("tool", "user")]}
            calls = {}
            request = urllib.request.Request(
                ENDPOINT, json.dumps(body).encode(),
                {"Content-Type": "application/json", "Authorization": "Bearer " + key}, method="POST")
            try:
                with urllib.request.urlopen(request, timeout=75) as response:
                    event["status"] = response.status
                    self.send_response(response.status)
                    self.send_header("Content-Type", "text/event-stream")
                    self.end_headers()
                    disconnected = False
                    for line in response:
                        if line.startswith(b"data: ") and line.strip() != b"data: [DONE]":
                            packet = json.loads(line[6:])
                            if packet.get("usage"):
                                event["usage"] = packet["usage"]
                            if packet.get("model"):
                                event["model"] = packet["model"]
                            for choice in packet.get("choices", []):
                                if choice.get("finish_reason"):
                                    event["finish"] = choice["finish_reason"]
                                delta = choice.get("delta", {})
                                event["content"] += delta.get("content") or ""
                                for call in delta.get("tool_calls") or []:
                                    entry = calls.setdefault(call["index"], {"id": "", "name": "", "arguments": ""})
                                    entry["id"] += call.get("id") or ""
                                    fn = call.get("function", {})
                                    entry["name"] += fn.get("name") or ""
                                    entry["arguments"] += fn.get("arguments") or ""
                        if not disconnected:
                            try:
                                self.wfile.write(line)
                                self.wfile.flush()
                            except (BrokenPipeError, ConnectionResetError):
                                disconnected = True
                    event["client_disconnected"] = disconnected
            except urllib.error.HTTPError as error:
                event.update(status=error.code, error=type(error).__name__)
                self.send_error(error.code, "Upstream refused request")
            except (OSError, ValueError) as error:
                event["error"] = type(error).__name__
                if "status" not in event:
                    self.send_error(502, "Upstream connection failed")
            finally:
                event["calls"] = [calls[i] for i in sorted(calls)]
                event["seconds"] = round(time.time() - started, 3)
                self.record(event)

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Relay)
    print("relay ready", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
