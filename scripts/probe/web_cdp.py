"""Drive headless Chrome over the DevTools protocol (stdlib only): load a URL, collect console
output and exceptions, dispatch mouse/key events and take screenshots.

Usage: python3 web_cdp.py <port> <out-dir> <steps.json>
Steps: [{"wait": s}, {"cdp": "Method", "params": {}}, {"upload": "/abs/path"}, {"shot": "name"}, {"click": [x, y], "button": "left|right", "mods": 0}, {"move": [x, y]}, {"text": "typed"},
        {"wheel": [x, y, dy]}, {"key": "Escape"} or {"key": "s", "mods": 2}, {"eval": "js"}, {"reload": true}]
Chrome must run with --remote-debugging-port=<port>.
"""
import base64, json, os, socket, struct, sys, time, urllib.request


class Ws:
    def __init__(self, url):
        host, path = url[len("ws://"):].split("/", 1)
        h, p = host.split(":")
        self.s = socket.create_connection((h, int(p)))
        key = base64.b64encode(os.urandom(16)).decode()
        self.s.sendall((f"GET /{path} HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                        f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            buf += self.s.recv(4096)
        self.rest = buf.split(b"\r\n\r\n", 1)[1]

    def _read(self, n):
        while len(self.rest) < n:
            d = self.s.recv(1 << 16)
            if not d:
                raise EOFError
            self.rest += d
        out, self.rest = self.rest[:n], self.rest[n:]
        return out

    def send(self, text):
        data = text.encode()
        head = bytes([0x81])
        n = len(data)
        if n < 126:
            head += bytes([0x80 | n])
        elif n < 65536:
            head += bytes([0x80 | 126]) + struct.pack(">H", n)
        else:
            head += bytes([0x80 | 127]) + struct.pack(">Q", n)
        mask = os.urandom(4)
        self.s.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def recv(self):
        msg = b""
        while True:
            b0, b1 = self._read(2)
            n = b1 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._read(8))[0]
            msg += self._read(n)
            if b0 & 0x80:
                return msg.decode()


class Cdp:
    def __init__(self, port):
        tabs = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json"))
        page = next(t for t in tabs if t["type"] == "page")
        self.ws = Ws(page["webSocketDebuggerUrl"])
        self.id = 0
        self.log = []

    def call(self, method, **params):
        self.id += 1
        self.ws.send(json.dumps({"id": self.id, "method": method, "params": params}))
        while True:
            m = json.loads(self.ws.recv())
            if m.get("id") == self.id:
                return m.get("result", m.get("error"))
            self._event(m)

    def _event(self, m):
        meth = m.get("method")
        if meth == "Runtime.consoleAPICalled":
            self.log.append(m["params"]["type"] + ": " + " ".join(str(a.get("value", a.get("description", ""))) for a in m["params"]["args"]))
        elif meth == "Page.fileChooserOpened":
            self.chooser = m["params"].get("backendNodeId")
            self.log.append("file chooser opened")
        elif meth in ("Page.downloadWillBegin", "Browser.downloadWillBegin"):
            self.log.append("download: " + m["params"].get("suggestedFilename", "") + " " + m["params"].get("url", "")[:40])
        elif meth == "Runtime.exceptionThrown":
            d = m["params"]["exceptionDetails"]
            self.log.append("EXCEPTION: " + d.get("exception", {}).get("description", d.get("text", "")))

    def pump(self, seconds):
        self.ws.s.settimeout(0.2)
        end = time.time() + seconds
        while time.time() < end:
            try:
                self._event(json.loads(self.ws.recv()))
            except (socket.timeout, TimeoutError):
                pass
        self.ws.s.settimeout(None)


def main():
    port, out, steps = sys.argv[1], sys.argv[2], json.load(open(sys.argv[3]))
    c = Cdp(port)
    c.call("Runtime.enable")
    c.call("Page.enable")
    for st in steps:
        if "wait" in st:
            c.pump(st["wait"])
        elif "shot" in st:
            r = c.call("Page.captureScreenshot", format="png")
            open(os.path.join(out, st["shot"] + ".png"), "wb").write(base64.b64decode(r["data"]))
        elif "click" in st:
            x, y = st["click"]
            btn = st.get("button", "left")
            mods = st.get("mods", 0)
            c.call("Input.dispatchMouseEvent", type="mouseMoved", x=x, y=y, modifiers=mods)
            c.call("Input.dispatchMouseEvent", type="mousePressed", x=x, y=y, button=btn, clickCount=1, modifiers=mods)
            c.call("Input.dispatchMouseEvent", type="mouseReleased", x=x, y=y, button=btn, clickCount=1, modifiers=mods)
            c.pump(0.4)
        elif "move" in st:
            x, y = st["move"]
            c.call("Input.dispatchMouseEvent", type="mouseMoved", x=x, y=y)
            c.pump(0.4)
        elif "text" in st:
            c.call("Input.insertText", text=st["text"])
            c.pump(0.4)
        elif "wheel" in st:
            x, y, dy = st["wheel"]
            c.call("Input.dispatchMouseEvent", type="mouseWheel", x=x, y=y, deltaX=0, deltaY=dy)
            c.pump(0.4)
        elif "key" in st:
            k = st["key"]
            codes = {"Escape": 27}
            # {"key": "s", "mods": 2}: CDP modifiers (1 Alt, 2 Ctrl, 4 Meta, 8 Shift); letters as code KeyS
            code = "Key" + k.upper() if len(k) == 1 and k.isalpha() else k
            vk = codes.get(k, ord(k.upper()) if len(k) == 1 else 0)
            for t in ("rawKeyDown", "keyUp"):
                c.call("Input.dispatchKeyEvent", type=t, key=k, code=code, windowsVirtualKeyCode=vk, modifiers=st.get("mods", 0))
            c.pump(0.4)
        elif "eval" in st:
            r = c.call("Runtime.evaluate", expression=st["eval"], returnByValue=True, awaitPromise=True)
            print("eval:", st["eval"], "->", json.dumps(r.get("result", r).get("value", r))[:400])
        elif "cdp" in st:
            print("cdp:", st["cdp"], "->", json.dumps(c.call(st["cdp"], **st.get("params", {})))[:300])
        elif "upload" in st:
            # after {"cdp": "Page.setInterceptFileChooserDialog", "params": {"enabled": true}} and a click that opens it
            print("upload:", c.call("DOM.setFileInputFiles", files=[st["upload"]], backendNodeId=c.chooser))
            c.pump(1)
        elif "reload" in st:
            c.call("Page.reload")
            c.pump(1)
    print("\n".join(c.log) if c.log else "(no console output)")


main()
