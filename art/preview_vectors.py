from pathlib import Path
import resvg_py, io
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[1]
import sys
group=sys.argv[1] if len(sys.argv)>1 else 'knots'
files=sorted((root/'art/svg').rglob('*.svg')) if group=='field' else sorted((root/'art/svg/knots').glob('*.svg'))
if group=='field': files=[f for f in files if 'knots' not in f.parts]
folder=root/'art/preview'; folder.mkdir(exist_ok=True)
for start in range(0,len(files),12):
    sheet=Image.new('RGB',(1440,1320),'white')
    for index,file in enumerate(files[start:start+12]):
        png=resvg_py.svg_to_bytes(svg_string=file.read_text(encoding='utf8'))
        im=Image.open(io.BytesIO(png)).convert('RGB'); im.thumbnail((480,300))
        sheet.paste(im,((index%3)*480,(index//3)*330))
        ImageDraw.Draw(sheet).text(((index%3)*480+8,(index//3)*330+307),file.stem,fill='black')
    sheet.save(folder/f'{group}-{start//12+1}.png')
print('Preview sheets ready')
