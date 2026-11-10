"""Local stage dashboard for the NewsRadar debug APK."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs
from html import escape
import argparse
import os
import secrets
import subprocess

PACKAGE = "com.ki_connect.newsradar"
CSRF = secrets.token_urlsafe(32)


def send_usb():
    command = [
        os.environ.get("NEWSRADAR_ADB", "adb"), "shell", "am", "broadcast",
        "-a", PACKAGE + ".USB_DEMO", "-n", PACKAGE + "/.UsbDemoReceiver",
    ]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=12, check=False)
    except FileNotFoundError as error:
        raise ValueError("ADB fehlt. Android Platform Tools installieren oder NEWSRADAR_ADB setzen.") from error
    except subprocess.TimeoutExpired as error:
        raise ValueError("Das Android-Gerät hat nicht rechtzeitig geantwortet.") from error
    if result.returncode != 0:
        raise ValueError("ADB konnte das Gerät nicht erreichen. USB-Debugging und Geräteauswahl prüfen.")
    if "Broadcast completed: result=1" in result.stdout:
        return "Android hat die Demo-Benachrichtigung ausgelöst."
    if "Broadcast completed: result=2" in result.stdout:
        raise ValueError("NewsRadar darf keine Benachrichtigungen anzeigen. Bitte auf Android erlauben.")
    raise ValueError("NewsRadar hat nicht geantwortet. Debug-APK installieren und einmal öffnen.")


def page(status=""):
    return f"""<!doctype html>
<html lang="de">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>NewsRadar | Bühnensteuerung</title>
<style>
  :root {{ color-scheme:dark; background:#0b1520; color:#eaf3f9; font:18px system-ui; }}
  main {{ max-width:48rem; padding:clamp(1.2rem,5vw,4rem); margin:auto; }}
  h1 {{ font-size:clamp(2.5rem,7vw,5rem); margin:.2em 0; line-height:1; }}
  header strong {{ color:#73edc9; letter-spacing:.12em; }}
  p {{ color:#b6cbd7; }}
  section {{ background:#152735; padding:1.2rem; margin:1rem 0; border-radius:1rem; }}
  button {{ font:inherit; padding:.7rem; border-radius:.5rem; }}
  button {{ color:#0b1520; background:#73edc9; border:0; font-weight:700; cursor:pointer; }}
  output {{ display:block; color:#ffcc78; }}
</style>
<main>
<header><strong>● NEWSRADAR / LIVE</strong><h1>Bühnendemo</h1></header>
<p>Neues KI-Labor für Niederbayern. Drei fiktive Quellen, zwei Starttermine und eine Benachrichtigung.</p>
<output role="status">{escape(status)}</output>
<section>
  <h2>USB</h2>
  <p>Handy per USB anschließen und USB-Debugging bestätigen.</p>
  <form method="post" action="/demo/usb">
    <input type="hidden" name="csrf" value="{CSRF}">
    <button type="submit">Demo über USB starten</button>
  </form>
</section>
</main>
</html>"""


class Dashboard(BaseHTTPRequestHandler):
    def valid_host(self):
        port = self.server.server_port
        return self.headers.get("Host") in {f"127.0.0.1:{port}", f"localhost:{port}"}

    def respond(self, status=200, message=""):
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(page(message).encode())

    def do_GET(self):
        if not self.valid_host() or self.path != "/":
            self.send_error(404)
            return
        self.respond()

    def do_POST(self):
        if not self.valid_host() or self.path != "/demo/usb":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 1 <= length <= 8192:
                raise ValueError()
            fields = parse_qs(self.rfile.read(length).decode("ascii"), max_num_fields=3)
            csrf = fields.get("csrf", [""])[0]
        except (ValueError, UnicodeError):
            self.send_error(400)
            return
        if not csrf.isascii() or not secrets.compare_digest(csrf, CSRF):
            self.send_error(403)
            return
        try:
            result = send_usb()
            self.respond(message=result)
        except ValueError as error:
            self.respond(422, str(error))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8787)
    args = parser.parse_args()
    with HTTPServer(("127.0.0.1", args.port), Dashboard) as server:
        print(f"NewsRadar: http://127.0.0.1:{server.server_port}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
