"""Loopback transport for Numen's native streaming Chat Completions client."""
import argparse
from hashlib import sha256
import json
import os
import re
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ENDPOINT = "https://api.deepseek.com/chat/completions"
DECISION_CHECKPOINT = (
    "决策节点：你刚收到了机械部件、朝向和入口的现场回执。"
    "下一次用 Lua 给出一两个自己选的局部候选，输出准备修改的格和一句连接依据，"
    "同时用现有 build.diff 检查候选，然后根据回执施工。"
    "若缺一项会改变选址或连接的事实，只说明该事实并查这一项。"
    "把方案留在工具调用和回执里，省去长篇说明。"
)
ENGINEERING_CHECKPOINT = (
    "施工前用一次 Lua 给出简短候选回执。每处拟改方块用 print 输出 "
    "ENGINEERING_CANDIDATE、方块状态、坐标、输入轴、输出轴及连接依据。无轴处写无。"
    "同次打印要检查的占用及实测通过条件，调用现有 numen.build.diff 核对这些候选格。"
    "若确实缺一项会改变方案的事实，只查这一项。回执后按现场结果推进施工，省去长篇推演。"
)
SURVEY_RECEIPT = re.compile(
    r"^parts [1-9][0-9]*[^\n]*\nfloor [^\n]+\ntwo-high floor-edge openings [1-9][0-9]*:",
    re.MULTILINE,
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("port", type=int)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    key = os.environ["DEEPSEEK_API_KEY"]
    thinking = os.environ.get("LAB_THINKING") == "1"
    trace = args.evidence.open("a", encoding="utf-8")
    decision_enabled = os.environ.get("LAB_MILL_DECISION") == "1"
    engineering_enabled = os.environ.get("LAB_ENGINEERING_RECEIPT") == "1"
    decision_sent = False

    class Relay(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def record(self, item):
            trace.write(json.dumps(item, ensure_ascii=False) + "\n")
            trace.flush()

        def do_POST(self):
            nonlocal decision_sent
            if self.path not in ("/chat/completions", "/v1/chat/completions"):
                self.send_error(404)
                return
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            if not thinking:
                body["thinking"] = {"type": "disabled"}
                body.pop("reasoning_effort", None)
            body["stream"] = True
            body["stream_options"] = {"include_usage": True}
            cap = int(os.environ.get("LAB_OUTPUT", "8192" if thinking else "4096"))
            body["max_tokens"] = min(body.get("max_tokens") or cap, cap)
            if thinking and os.environ.get("LAB_REASONING"):
                body["thinking"] = {"type": "enabled"}
                body["reasoning_effort"] = os.environ["LAB_REASONING"]
            messages = body.get("messages", [])
            # A one-shot instruction in the lab, based only on the agent's real receipt.
            checkpoint = (decision_enabled or engineering_enabled) and not decision_sent and any(
                m.get("role") == "tool" and SURVEY_RECEIPT.search(m.get("content") or "")
                for m in messages
            )
            if checkpoint:
                if engineering_enabled and messages[-1].get("role") == "user" and (
                    messages[-1].get("content") or ""
                ).startswith("<runtime_state>"):
                    runtime = messages[-1]
                    body["messages"] = [*messages[:-1], {
                        **runtime, "content": runtime["content"] + "\n<lab_checkpoint>"
                        + ENGINEERING_CHECKPOINT + "</lab_checkpoint>",
                    }]
                    checkpoint_position = "runtime_state"
                else:
                    instruction = ENGINEERING_CHECKPOINT if engineering_enabled else DECISION_CHECKPOINT
                    body["messages"] = [{"role": "system", "content": instruction}, *messages]
                    checkpoint_position = "system"
                decision_sent = True
            started = time.time()
            event = {"utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(started)),
                     "outputLimit": body["max_tokens"], "model": body.get("model"), "content": "", "calls": [],
                     "tools": [tool["function"]["name"] for tool in body.get("tools", [])],
                     "context": [{"role": m.get("role"), "content": m.get("content"),
                                  "tool_call_id": m.get("tool_call_id")}
                                 for m in messages[-4:] if m.get("role") in ("tool", "user")]}
            if checkpoint:
                if engineering_enabled:
                    event["engineeringReceiptPrompt"] = checkpoint_position
                else:
                    event["decisionCheckpoint"] = True
            calls = {}
            reasoning = sha256()
            reasoning_bytes = 0
            if thinking:
                # Verify exact replay without recording private reasoning text.
                event["thinking"] = body.get("thinking")
                event["reasoningEffort"] = body.get("reasoning_effort")
                event["maxTokens"] = body["max_tokens"]
                event["replayedReasoning"] = [
                    {"ids": [c["id"] for c in m["tool_calls"]],
                     "sha256": sha256((m.get("reasoning_content") or "").encode()).hexdigest()}
                    for m in messages if m.get("role") == "assistant" and m.get("tool_calls")]
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
                                if thinking:
                                    chunk = (delta.get("reasoning_content") or "").encode()
                                    reasoning.update(chunk)
                                    reasoning_bytes += len(chunk)
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
                if thinking:
                    event["reasoningBytes"] = reasoning_bytes
                    event["reasoningSha256"] = reasoning.hexdigest()
                event["seconds"] = round(time.time() - started, 3)
                self.record(event)

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Relay)
    print("relay ready", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
