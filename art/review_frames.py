from pathlib import Path
import re, requests, io
from PIL import Image, ImageDraw
folder=Path('art/reference')
tiles=[]
entries=[]
for file in folder.glob('*.html'):
    html=file.read_text(encoding='utf8')
    name=re.search(r"var File = '([^']+)'",html)
    count=re.search(r'var AutoImages = (\d+)',html)
    if not name or not count: continue
    entries.append((name[1],int(count[1])))
entries += [('fig8_',4),('twohalfhitches',6),('lashdiagonal',15),('lashshear',11)]
for name,count in entries:
    for i in range(1,count+1):
        out=folder/f'{name}-{i}.jpg'
        if not out.exists():
            response=requests.get(f'https://www.animatedknots.com/photos/{name}/{name}R{i}.jpg',timeout=30)
            if response.status_code != 200: continue
            out.write_bytes(response.content)
        try:
            im=Image.open(out).convert('RGB'); im.thumbnail((240,230))
            tile=Image.new('RGB',(250,260),'white'); tile.paste(im,((250-im.width)//2,22)); ImageDraw.Draw(tile).text((5,4),f'{name} {i}',fill='black'); tiles.append(tile)
        except Exception: pass
sheet=Image.new('RGB',(1250,260*((len(tiles)+4)//5)),'#eeeeee')
for n,im in enumerate(tiles): sheet.paste(im,((n%5)*250,(n//5)*260))
sheet.save(folder/'contact.png')
print('Review frames:',len(tiles))
