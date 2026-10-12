from pathlib import Path
import requests, fitz
p=Path('art/reference/baloo.pdf')
if not p.exists(): p.write_bytes(requests.get('https://filestore.scouting.org/filestore/training/pdf/510-033(17)BALOO.pdf',timeout=60).content)
doc=fitz.open(p)
doc[97].get_pixmap(matrix=fitz.Matrix(1.5,1.5)).save('art/reference/baloo-knots.png')
