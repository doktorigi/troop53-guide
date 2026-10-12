"""Attach authored diagrams immediately after their steps; keep content editable."""
from pathlib import Path
import re, os
from PIL import Image
root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
for source,name in [('exec-c483a075-862b-42a7-ae01-1fa2c25a47c7.png','tent-rainfly'),('exec-389e481e-7447-496e-93fd-4e228c4d00e5.png','campsite-comparison')]:
    dest=assets/'img/camping'/f'{name}.webp'; dest.parent.mkdir(parents=True,exist_ok=True)
    if not dest.exists():
        imagegen_dir=os.environ.get('TROOP53_IMAGEGEN_DIR')
        if not imagegen_dir:
            raise SystemExit('Missing packaged camping image. Set TROOP53_IMAGEGEN_DIR to the original image-generation output folder.')
        im=Image.open(Path(imagegen_dir)/source).convert('RGB')
        im.resize((1080,round(im.height*1080/im.width)),Image.Resampling.LANCZOS).save(dest,'WEBP',quality=82,method=6)

inserts={
 'compass-parts':[('## Directions in degrees','navigation/compass.xml','The baseplate, travel arrow, rotating housing, magnetic needle, and orienting arrow.')],
 'bearings':[('> Going back?','navigation/red-in-shed.xml','Hold the compass level. Turn until the red needle aligns with the orienting arrow.')],
 'topo-maps':[('## Scale','navigation/contours.xml','Closed contours climb toward the hilltop. Valley contours point upstream, against the stream flow.')],
 'natural-navigation':[('## North Star','navigation/shadow-stick.xml','First shadow tip is roughly west; second tip is roughly east. Mark the tips 15–20 minutes apart.'),('> Moss','navigation/polaris.xml','Extend the line from Merak through Dubhe about five pointer-star spacings to Polaris.')],
 'fire-building':[('> Wood','fire/lays.xml','Teepee, log cabin, lean-to, and star fire lays, shown before lighting.')],
 'fire-out':[('! It is not','fire/drown-stir-feel.xml','Drown with water, stir thoroughly, and check for heat with your hand near the ashes. Repeat until cold.')],
 'cpr':[('4. Push','firstaid/cpr.xml','For an adult or teen, center stacked hands on the chest; keep elbows straight and shoulders over hands.')],
 'ticks':[('1.','firstaid/tick.xml','Grip the tick close to the skin with fine-tipped tweezers and pull steadily upward; do not twist.')],
 'fractures':[('6. For','firstaid/splint.xml','Pad a forearm splint, support past the joints, secure away from the injury, and keep fingers visible.'),('7. Treat','firstaid/sling.xml','A sling supports the forearm. Pad the knot and leave fingers visible to check circulation.')],
 'asthma-seizure':[('# END','firstaid/recovery.xml','After a seizure, if breathing normally, rest on the side with the airway open and upper knee bent. Keep checking breathing.')],
 'poison-plants':[('## If you','nature/poison-plants.xml','Ivy: three leaflets with a longer middle stalk. Oak: three lobed leaflets. Sumac: 7–13 smooth leaflets on red stems. Shapes vary.')],
 'emergency-shelter':[('! Avoid','survival/debris-hut.xml','Debris hut cross-section: sturdy ridgepole and ribs, at least two feet of dry debris, and thick ground insulation.')],
 'signaling':[('# END','survival/signals.xml','Large ground-to-air signals: V means need help; X means need medical help. A whistle signal is three blasts, repeated.')],
 'water-rescue':[('! Shout','water/rescue.xml','Reach from a stable position, throw flotation, row with safe equipment, and leave swimming rescues to trained rescuers.')],
 'camp-layout':[('## Duty','camping/layout.xml','Keep sleeping and cooking areas separate. In bear country, the guide calls for at least 200 feet, with the kitchen downwind.')],
 'campsite':[('# END','camping/campsite-comparison.webp','Left: level established ground clear of overhead hazards. Right: avoid dry washes and dead or hanging branches.')],
 'tents':[('# END','camping/tent-rainfly.webp','A separate rainfly sheds rain while leaving an air gap above the inner tent; secure guylines clear of the doorway.')],
}
for file in sorted((assets/'guide').glob('*.txt')):
    text=file.read_text(encoding='utf8')
    # Idempotent: remove only generated image lines, then reinsert.
    text='\n'.join(line for line in text.splitlines() if not line.startswith('@ '))
    if file.name=='06-knots.txt':
        text=text.replace('Square Knot | Join two ropes of the same size','Square Knot | Bind bandages and bundles')
        text=text.replace('Bowline | A loop that will not slip','Bowline | A fixed loop for camp tasks')
        text=text.replace('Shear Lashing | Make an A-frame or tripod','Shear Lashing | Make a two-pole A-frame')
        text=text.replace('A rescue knot. The loop does not tighten under load, so it can go around a person.','A fixed loop for ordinary camp tasks. The loop does not tighten under steady load, but a bowline can loosen when unloaded.')
        text=text.replace('> Practice tying it around your own waist and with your eyes closed.','> Practice with spare rope until you can identify the nipping loop, collar, and tail.\n! These diagrams are for camp practice. Climbing and rescue require trained supervision, approved equipment, and inspected knots.')
        text=text.replace('5. Spread the poles apart to make an A-frame. For a tripod, use three poles and weave the wraps over and under.','5. Spread the poles apart to make an A-frame. A tripod uses a separate three-pole lashing with over-and-under weaving; do not substitute this two-pole lashing.')
        lines=[]; article=''
        for line in text.splitlines():
            if line.startswith('# '): article=line[2:].split('|')[0].strip()
            lines.append(line)
            m=re.match(r'(\d+)\. (.+)',line)
            if m: lines.append(f'@ knots/{article}-{m[1]}.xml | Step {m[1]}: {m[2]}')
        text='\n'.join(lines)
    # Work article-by-article so sentinels cannot accidentally attach to another guide.
    chunks=re.split(r'(?m)(?=^# )',text)
    for i,chunk in enumerate(chunks):
        if not chunk.startswith('# '): continue
        article=chunk[2:].split('|')[0].strip()
        for marker,path,caption in inserts.get(article,[]):
            block=f'@ {path} | {caption}\n'
            if marker=='# END': chunk=chunk.rstrip()+'\n'+block+'\n'
            else:
                lines=chunk.splitlines(True)
                pos=next((n for n,l in enumerate(lines) if l.startswith(marker)),None)
                if pos is None: raise ValueError((article,marker))
                lines.insert(pos,block); chunk=''.join(lines)
        if article=='flag-folding':
            lines=[]
            for line in chunk.splitlines():
                lines.append(line)
                m=re.match(r'(\d+)\. (.+)',line)
                if m: lines.append(f'@ flag/fold-{m[1]}.xml | Step {m[1]}: {m[2]}')
            chunk='\n'.join(lines)+'\n\n'
        chunks[i]=chunk
    file.write_text(''.join(chunks).rstrip()+'\n',encoding='utf8')
print('Art attached to guide content.')
