"""Export the existing artwork for iOS without redrawing or generating technical art.

Authoring dependencies: pip install fonttools reportlab pillow resvg-py
Run from anywhere: python ios/tools/prepare_art.py
"""
from pathlib import Path
import hashlib, json, io, xml.etree.ElementTree as ET
from fontTools.pens.basePen import BasePen
from fontTools.svgLib.path import parse_path
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from PIL import Image
import resvg_py

ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'ios/Troop53Guide/Resources/Illustrations'

class PDFPen(BasePen):
    def __init__(self, drawing):
        super().__init__(None); self.path=drawing.beginPath()
    def _moveTo(self,p): self.path.moveTo(*p)
    def _lineTo(self,p): self.path.lineTo(*p)
    def _curveToOne(self,a,b,c): self.path.curveTo(*(a+b+c))
    def _qCurveToOne(self,a,b):
        start=self._getCurrentPoint()
        first=tuple(start[i]+2*(a[i]-start[i])/3 for i in [0,1])
        second=tuple(b[i]+2*(a[i]-b[i])/3 for i in [0,1])
        self.path.curveTo(*(first+second+b))
    def _closePath(self): self.path.close()

def svg_pdf(source,destination):
    root=ET.parse(source).getroot(); _,_,w,h=map(float,root.attrib['viewBox'].split())
    destination.parent.mkdir(parents=True,exist_ok=True)
    pdf=canvas.Canvas(str(destination),pagesize=(w,h),pageCompression=1,invariant=1)
    pdf.setTitle('Troop 53 Scout Guide — '+source.stem); pdf.setAuthor('Benjamin Doktor')
    pdf.translate(0,h); pdf.scale(1,-1)
    for node in root:
        if node.tag.split('}')[-1]!='path': raise ValueError('Unexpected SVG element: '+node.tag)
        pen=PDFPen(pdf); parse_path(node.attrib['d'],pen)
        fill=node.attrib.get('fill','none'); stroke=node.attrib.get('stroke')
        if fill!='none': pdf.setFillColor(HexColor(fill))
        if stroke:
            pdf.setStrokeColor(HexColor(stroke)); pdf.setLineWidth(float(node.attrib.get('stroke-width','1')))
            pdf.setLineCap({'butt':0,'round':1,'square':2}[node.attrib.get('stroke-linecap','butt')])
            pdf.setLineJoin({'miter':0,'round':1,'bevel':2}[node.attrib.get('stroke-linejoin','miter')])
        pdf.drawPath(pen.path,fill=int(fill!='none'),stroke=int(stroke is not None),fillMode=1)
    pdf.showPage(); pdf.save()

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    records=[]
    for source in sorted((ROOT/'art/svg').rglob('*.svg')):
        relative=source.relative_to(ROOT/'art/svg')
        destination=DEST/relative.with_suffix('.pdf'); svg_pdf(source,destination)
        records.append({'source':source.relative_to(ROOT).as_posix(),'sha256':sha(source),'output':destination.relative_to(ROOT).as_posix(),'outputSha256':sha(destination)})
    for source in sorted((ROOT/'app/src/main/assets/img').rglob('*.webp')):
        destination=DEST/source.relative_to(ROOT/'app/src/main/assets/img').with_suffix('.jpg')
        destination.parent.mkdir(parents=True,exist_ok=True)
        Image.open(source).convert('RGB').save(destination,'JPEG',quality=90,optimize=True)
        records.append({'source':source.relative_to(ROOT).as_posix(),'sha256':sha(source),'output':destination.relative_to(ROOT).as_posix(),'outputSha256':sha(destination)})
    # Use the existing code-drawn Android shield paths for the native iOS launcher icon.
    icon=ET.parse(ROOT/'app/src/main/res/drawable/ic_launcher_fg.xml').getroot()
    ns='{http://schemas.android.com/apk/res/android}'
    parts=['<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="22 19 64 74">','<path d="M22 19H86V93H22Z" fill="#1F3A24"/>']
    for node in icon:
        attributes={'d':node.attrib[ns+'pathData'],'fill':node.attrib.get(ns+'fillColor','none')}
        for xml,svg in [('strokeColor','stroke'),('strokeWidth','stroke-width'),('strokeLineCap','stroke-linecap'),('strokeLineJoin','stroke-linejoin')]:
            if ns+xml in node.attrib: attributes[svg]=node.attrib[ns+xml]
        parts.append('<path '+' '.join(f'{key}="{value}"' for key,value in attributes.items())+'/>')
    parts.append('</svg>')
    png=resvg_py.svg_to_bytes(svg_string='\n'.join(parts))
    folder=ROOT/'ios/Troop53Guide/Resources/Assets.xcassets/AppIcon.appiconset'; folder.mkdir(parents=True,exist_ok=True)
    Image.open(io.BytesIO(png)).convert('RGB').save(folder/'AppIcon.png')
    (folder/'Contents.json').write_text(json.dumps({'images':[{'filename':'AppIcon.png','idiom':'universal','platform':'ios','size':'1024x1024'}],'info':{'author':'xcode','version':1}},indent=2)+'\n',encoding='utf8')
    (folder.parent/'Contents.json').write_text('{"info":{"author":"xcode","version":1}}\n',encoding='utf8')
    (ROOT/'ios/art-manifest.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf8')
    print(f'Exported {len(records)} existing illustrations and the app icon.')

if __name__=='__main__': main()
