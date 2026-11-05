"""Assemble the web (PWA) edition into build/web from the same content the apps ship.

    python tools/build_web.py           # build
    python tools/build_web.py --serve   # build, then serve at http://localhost:8053
"""
import hashlib, json, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
OUT = ROOT / "build/web"


def build():
    shutil.rmtree(OUT, ignore_errors=True)
    shutil.copytree(ROOT / "web", OUT)
    # Android vectors (.xml) are compiled from art/svg; the web uses the SVG originals at the same path.
    shutil.copytree(ROOT / "art/svg", OUT / "img")
    for f in (ASSETS / "img").rglob("*.webp"):
        dest = OUT / "img" / f.relative_to(ASSETS / "img")
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(f, dest)
    guide = "\n".join(p.read_text(encoding="utf-8") for p in sorted((ASSETS / "guide").glob("*.txt")))
    (OUT / "guide.txt").write_text(guide, encoding="utf-8", newline="\n")
    shutil.copy2(ASSETS / "quiz.txt", OUT / "quiz.txt")

    # Every image the guide references must exist, or offline precache (and the page) breaks.
    refs = {line.split("|")[0][2:].strip() for line in guide.splitlines() if line.strip().startswith("@ ")}
    refs |= {f"headers/{line.split('|')[0].strip('= ')}.xml" for line in guide.splitlines() if line.startswith("===")}
    missing = [r for r in refs if not (OUT / "img" / r.replace(".xml", ".svg")).exists()]
    assert not missing, f"missing images: {missing}"

    files = sorted(p.relative_to(OUT).as_posix() for p in OUT.rglob("*") if p.is_file() and p.name != "sw.js")
    digest = hashlib.sha256(b"".join((OUT / f).read_bytes() for f in files)).hexdigest()[:12]
    sw = (OUT / "sw.js").read_text(encoding="utf-8")
    (OUT / "sw.js").write_text(sw.replace("__VERSION__", "t53-" + digest).replace("__FILES__", json.dumps(["./"] + files)), encoding="utf-8")
    print(f"built {OUT} ({len(files)} files, {len(refs)} images referenced, version t53-{digest})")


if __name__ == "__main__":
    build()
    if "--serve" in sys.argv:
        import functools, http.server
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=OUT)
        print("serving http://localhost:8053")
        http.server.ThreadingHTTPServer(("", 8053), handler).serve_forever()
