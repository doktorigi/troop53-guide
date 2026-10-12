"""Review-only downloads, never packaged or redistributed. Run from repo root."""
from pathlib import Path
import re, requests
from PIL import Image, ImageDraw
folder = Path('art/reference')
folder.mkdir(exist_ok=True)
names = ['bowline-knot', 'square-knot', 'sheet-bend-knot', 'figure-8-knot',
         'clove-hitch-knot-rope-end', 'timber-hitch-knot', 'two-half-hitches-knot',
         'square-lashing-knot', 'diagonal-lashing-knot', 'shear-lashing-knot', 'common-whipping-knot']
for name in names:
    html = requests.get('https://www.animatedknots.com/' + name, timeout=30).text
    folder.joinpath(name+'.html').write_text(html, encoding='utf8')
    urls = re.findall(r'<img[^>]+src=[\"\']([^\"\']+)', html)
    urls = [u for u in urls if 'gif' in u or 'jpg' in u]
    snippets = re.findall(r'.{0,70}(?:animation|var knot|var dir|R1.jpg|image_name|Image\().{0,150}', html)
    print(name, snippets[:6])
