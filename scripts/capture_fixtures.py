#!/usr/bin/env python3
# partstrack - a small inventory for electronic parts
# Copyright (C) 2026 Kayla Theil (accounts@kaylatheil.de)
#
#     This program is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This program is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <https://www.gnu.org/licenses/>.

"""Capture the JSON answers of a real partstrack-web for the API client's tests.

    python scripts/capture_fixtures.py [PARTSTRACK_CHECKOUT]

Starts partstrack-web from a partstrack checkout (default: ../partstrack,
which needs Flask and waitress) on a scratch database in a temporary folder,
fills it through the API, and writes the answers to
app/src/test/resources/api/. A second server shows a read-only copy of it, for
the answers that only such a copy gives. Never touches a real inventory, and
needs no internet access. Run it again when the API changes.
"""

from __future__ import annotations

import json
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "test" / "resources" / "api"
PORT, COPY_PORT = 8765, 8766

# The smallest valid PNG (1x1 pixel), as an uploaded picture.
PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
    "1f15c4890000000d49444154789c6360f8cfc0f01f0005000201e2f5d3d1"
    "0000000049454e44ae426082"
)


class Server:
    def __init__(self, checkout: Path, db: Path, port: int):
        self.base = f"http://127.0.0.1:{port}"
        self.process = subprocess.Popen(
            [sys.executable, "-m", "partstrack.web", "--db", str(db), "--host", "127.0.0.1", "--port", str(port)],
            cwd=checkout, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        for _ in range(100):
            try:
                urllib.request.urlopen(self.base + "/api", timeout=1).close()
                return
            except OSError:
                time.sleep(0.1)
        self.process.kill()
        raise SystemExit(f"partstrack-web didn't start on port {port}")

    def stop(self) -> None:
        self.process.terminate()
        self.process.wait()

    def call(self, method: str, path: str, body: object = None, token: str | None = None,
             upload: tuple[str, str, bytes] | None = None) -> tuple[int, bytes]:
        headers = {}
        data = None
        if token is not None:
            headers["Authorization"] = f"Bearer {token}"
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if upload is not None:
            name, media_type, content = upload
            boundary = uuid.uuid4().hex
            data = (
                f"--{boundary}\r\nContent-Disposition: form-data; name=\"kind\"\r\n\r\nimage\r\n"
                f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
                f"Content-Type: {media_type}\r\n\r\n"
            ).encode() + content + f"\r\n--{boundary}--\r\n".encode()
            headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()


def save(name: str, answer: tuple[int, bytes], expected: int) -> dict | list:
    status, body = answer
    if status != expected:
        raise SystemExit(f"{name}: expected HTTP {expected}, got {status}: {body.decode(errors='replace')}")
    data = json.loads(body)
    (OUT / f"{name}.json").write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return data


def expect(name: str, answer: tuple[int, bytes], expected: int) -> None:
    if answer[0] != expected:
        raise SystemExit(f"{name}: expected HTTP {expected}, got {answer[0]}: {answer[1].decode(errors='replace')}")


def main() -> None:
    checkout = Path(sys.argv[1] if len(sys.argv) > 1 else ROOT.parent / "partstrack").resolve()
    sys.path.insert(0, str(checkout))
    from partstrack import __version__
    from partstrack.snapshot import open_inventory, unpack_copy
    from partstrack.store import _token_hash

    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()

    with tempfile.TemporaryDirectory(prefix="partstrack-fixtures-") as tmp:
        db = Path(tmp) / "inventory" / "parts.db"
        db.parent.mkdir()
        with open_inventory(db) as inv:
            token = inv.create_token("phone")

        server = Server(checkout, db, PORT)
        try:
            s = server.call
            # Changes, in an order that leaves an interesting inventory behind.
            save("add_location", s("POST", "/api/locations", {"path": "Cabinet/Drawer 1", "description": "Resistors"}, token), 201)
            resistor = save("add_part", s("POST", "/api/parts", {
                "name": "10k resistor", "description": "Thick film resistor, 1%", "manufacturer": "Yageo",
                "mpn": "RC0603FR-0710KL", "category": "Resistor", "package": "0603", "notes": "",
                "tags": ["smd", "resistor"], "attributes": {"resistance": "10k", "tolerance": "1%"},
                "min_quantity": 50, "stock": [{"location": "Cabinet/Drawer 1", "quantity": 100}],
            }, token), 201)
            rid = resistor["id"]
            s("POST", "/api/parts", {"name": "NE555", "description": "Timer", "category": "IC", "package": "DIP-8",
                                     "tags": ["ic"], "min_quantity": 5,
                                     "stock": [{"location": "Shelf", "quantity": 3}]}, token)
            minimal = save("add_part_minimal", s("POST", "/api/parts", {"name": "Mystery part"}, token), 201)
            save("update_part", s("PATCH", f"/api/parts/{minimal['id']}",
                                  {"notes": "Found in a drawer", "min_quantity": None, "tags": ["unsorted"]}, token), 200)
            expect("delete_part", s("DELETE", f"/api/parts/{minimal['id']}", token=token), 204)
            save("stock_delta", s("POST", f"/api/parts/{rid}/stock",
                                  {"location": "Cabinet/Drawer 1", "delta": -3, "note": "for the amplifier"}, token), 200)
            save("stock_set", s("POST", f"/api/parts/{rid}/stock",
                                {"location": "Cabinet/Drawer 2", "quantity": 20}, token), 200)
            save("move", s("POST", f"/api/parts/{rid}/move",
                           {"from": "Cabinet/Drawer 2", "to": "Shelf", "quantity": 5, "note": "sorting"}, token), 200)
            save("attachment_upload", s("POST", f"/api/parts/{rid}/attachments", token=token,
                                        upload=("photo.png", "image/png", PNG)), 201)
            save("attachment_link", s("POST", f"/api/parts/{rid}/attachments",
                                      {"url": "https://www.example.com/rc0603.pdf", "kind": "datasheet",
                                       "download": False}, token), 201)
            # The server refuses to download from its own address, which gives a
            # download error without needing the internet.
            failed = save("attachment_link_download_error", s("POST", f"/api/parts/{rid}/attachments",
                                                              {"url": f"http://127.0.0.1:{PORT}/notes.pdf"}, token), 201)
            save("error_download", s("POST", f"/api/parts/{rid}/attachments/{failed['id']}/download", token=token), 400)
            expect("remove_attachment", s("DELETE", f"/api/parts/{rid}/attachments/{failed['id']}", token=token), 204)
            box = s("POST", "/api/locations", {"path": "Box"}, token)
            box_id = json.loads(box[1])["id"]
            save("update_location", s("PATCH", f"/api/locations/{box_id}",
                                      {"name": "Small box", "description": "On the desk", "parent": "Cabinet"}, token), 200)
            expect("delete_location", s("DELETE", f"/api/locations/{box_id}", token=token), 204)

            # Reading.
            index = save("index", s("GET", "/api"), 200)
            if index["version"] != __version__:
                raise SystemExit(f"the server says version {index['version']}, the checkout {__version__}")
            save("token", s("GET", "/api/token", token=token), 200)
            save("parts", s("GET", "/api/parts"), 200)
            save("parts_search", s("GET", "/api/parts?q=resistance%3E%3D1k&tag=smd"), 200)
            save("parts_low", s("GET", "/api/parts?low=1"), 200)
            save("part", s("GET", f"/api/parts/{rid}"), 200)
            locations = save("locations", s("GET", "/api/locations"), 200)
            save("location", s("GET", f"/api/locations/{locations[0]['id']}"), 200)
            save("tags", s("GET", "/api/tags"), 200)
            save("categories", s("GET", "/api/categories"), 200)
            save("history", s("GET", f"/api/history?part={rid}&limit=10"), 200)

            # Errors.
            save("error_unknown_field", s("POST", "/api/parts", {"name": "x", "colour": "red"}, token), 400)
            save("error_bad_filter", s("GET", "/api/parts?q=resistance%3Eabc"), 400)
            save("error_location_not_empty", s("DELETE", f"/api/locations/{locations[0]['id']}", token=token), 400)
            save("error_no_token", s("POST", "/api/locations", {"path": "X"}), 401)
            save("error_bad_token", s("GET", "/api/token", token="pt_not-a-token"), 401)
            save("error_part_not_found", s("GET", "/api/parts/9999"), 404)
            save("error_no_endpoint", s("GET", "/api/nothing"), 404)
            save("error_method", s("PUT", f"/api/parts/{rid}", {}, token), 405)

            status, archive = s("GET", "/api/snapshot")
            expect("snapshot", (status, b""), 200)
        finally:
            server.stop()

        # A read-only copy. Snapshots leave the tokens out, so put one in directly.
        (Path(tmp) / "snapshot.tar.gz").write_bytes(archive)
        copy_db = unpack_copy(Path(tmp) / "snapshot.tar.gz", Path(tmp) / "copy", source=server.base)
        with sqlite3.connect(copy_db) as conn:
            conn.execute("INSERT INTO api_tokens (name, hash) VALUES ('phone', ?)", (_token_hash(token),))
        conn.close()
        copy = Server(checkout, copy_db, COPY_PORT)
        try:
            save("token_read_only", copy.call("GET", "/api/token", token=token), 200)
            save("error_read_only", copy.call("POST", "/api/locations", {"path": "X"}, token), 409)
        finally:
            copy.stop()

    print(f"Wrote {len(list(OUT.glob('*.json')))} answers of partstrack {__version__} to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
