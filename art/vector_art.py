"""Original, deterministic SVG art. Identical paths are emitted as native VectorDrawables.

Run from repo root: python art/vector_art.py
Authoring tools only: fonttools (labels -> path outlines), resvg-py/Pillow (review sheets).
No third-party artwork or fonts are bundled. Android has no added dependencies.
"""
from pathlib import Path
from xml.sax.saxutils import escape
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
import math, re, json, os

ROOT=Path(__file__).resolve().parents[1]
PAPER='#F4EEDF'; RED='#B5332A'; TAN='#D9C79A'; INK='#39372E'; GREEN='#1F3A24'
BLUE='#24577A'; WOOD='#B89A70'; LIGHT='#E5D8BA'
font=TTFont(os.environ.get('TROOP53_ART_FONT','C:/Windows/Fonts/arial.ttf'))
glyphs=font.getGlyphSet(); cmap=font.getBestCmap(); units=font['head'].unitsPerEm
manifest=[]

def cubic_section(points,t0,t1):
    """Exact subcurve for a crossing mask; it cannot add a stray rope segment."""
    def point(t):
        return tuple((1-t)**3*points[0][i]+3*(1-t)**2*t*points[1][i]+3*(1-t)*t*t*points[2][i]+t**3*points[3][i] for i in [0,1])
    def derivative(t):
        return tuple(3*(1-t)**2*(points[1][i]-points[0][i])+6*(1-t)*t*(points[2][i]-points[1][i])+3*t*t*(points[3][i]-points[2][i]) for i in [0,1])
    a=point(t0); b=point(t1); da=derivative(t0); db=derivative(t1); delta=(t1-t0)/3
    coords=[a,tuple(a[i]+da[i]*delta for i in [0,1]),tuple(b[i]-db[i]*delta for i in [0,1]),b]
    return f'M{coords[0][0]:.3f} {coords[0][1]:.3f} C'+' '.join(f'{x:.3f} {y:.3f}' for x,y in coords[1:])

class Art:
    def __init__(self,title='',note=''):
        self.paths=[]
        self.path('M0 0 H720 V450 H0 Z',PAPER)
        if title: self.text(title,28,40,23,GREEN)
        if note: self.text(note,28,69,15,INK)
    def path(self,d,fill='none',stroke=None,width=1):
        self.paths.append((d,fill,stroke,width))
    def line(self,x,y,u,v,color=INK,w=2): self.path(f'M{x} {y} L{u} {v}',stroke=color,width=w)
    def rect(self,x,y,w,h,fill,stroke=None): self.path(f'M{x} {y} h{w} v{h} h{-w} Z',fill,stroke,2)
    def circle(self,x,y,r,fill,stroke=None): self.path(f'M{x-r} {y} a{r} {r} 0 1 0 {2*r} 0 a{r} {r} 0 1 0 {-2*r} 0',fill,stroke,2)
    def star(self,x,y,r,fill):
        points=[]
        for i in range(10):
            angle=math.radians(-90+i*36); radius=r if i%2==0 else r*.4
            points.append((x+math.cos(angle)*radius,y+math.sin(angle)*radius))
        self.path('M'+' L'.join(f'{u:.2f} {v:.2f}' for u,v in points)+' Z',fill)
    def text(self,text,x,y,size=17,color=INK):
        scale=size/units; parts=[]; length=0
        for ch in text:
            pen=SVGPathPen(glyphs, ntos=lambda v: f'{v:.2f}'.rstrip('0').rstrip('.') if v != 0 else '0')
            name=cmap.get(ord(ch),'space')
            glyphs[name].draw(TransformPen(pen,(scale,0,0,-scale,x,y)))
            d=pen.getCommands()
            if length+len(d)>12000:
                self.path(' '.join(parts),color); parts=[]; length=0
            if d: parts.append(d); length+=len(d)
            x += font['hmtx'][name][0]*scale
        if parts: self.path(' '.join(parts),color)
    def rope(self,d,color=TAN,w=12,gap=False):
        if gap: self.path(d,stroke=PAPER,width=w+12)
        self.path(d,stroke=INK,width=w+4)
        self.path(d,stroke=color,width=w)
    def arrow(self,d,x,y,angle=0,color=RED):
        self.path(d,stroke=color,width=3)
        a=math.radians(angle)
        points=[(x,y),(x-12*math.cos(a)+6*math.sin(a),y-12*math.sin(a)-6*math.cos(a)),(x-12*math.cos(a)-6*math.sin(a),y-12*math.sin(a)+6*math.cos(a))]
        self.path('M'+' L'.join(f'{u:.2f} {v:.2f}' for u,v in points)+' Z',color)
    def pole(self,x,y,w,h):
        self.rect(x,y,w,h,WOOD,INK)
        self.line(x+8,y+8,x+8,y+h-8,LIGHT,3)
        self.line(x+w-8,y+18,x+w-8,y+h-18,'#96764B',2)
    def legend(self,second=False):
        self.rope('M30 415 H65',RED,8)
        self.text('working end' if not second else 'rope A',76,420,14)
        self.rope('M235 415 H270',TAN,8)
        self.text('standing part' if not second else 'rope B',281,420,14)
        self.text('Gaps show a rope passing behind.',455,420,13)
    def save(self,key,caption='',source='',check=''):
        svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="675" viewBox="0 0 720 450">']
        xml=['<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="720dp" android:height="450dp" android:viewportWidth="720" android:viewportHeight="450">']
        for d,fill,stroke,w in self.paths:
            d=escape(d,{'"':'&quot;'})
            svg.append(f'<path d="{d}" fill="{fill}"'+(f' stroke="{stroke}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"' if stroke else '')+'/>')
            xml.append(f'<path android:pathData="{d}" android:fillColor="{fill if fill != "none" else "#00000000"}"'+(f' android:strokeColor="{stroke}" android:strokeWidth="{w}" android:strokeLineCap="round" android:strokeLineJoin="round"' if stroke else '')+'/>')
        svg.append('</svg>'); xml.append('</vector>')
        p=ROOT/'art/svg'/f'{key}.svg'; p.parent.mkdir(parents=True,exist_ok=True); p.write_text('\n'.join(svg),encoding='utf8')
        p=ROOT/'app/src/main/assets/img'/f'{key}.xml'; p.parent.mkdir(parents=True,exist_ok=True); p.write_text('\n'.join(xml),encoding='utf8')
        p=ROOT/'app/src/main/res/drawable'/('img_'+key.replace('/','_').replace('-','_')+'.xml'); p.write_text('\n'.join(xml),encoding='utf8')
        manifest.append(dict(path=key+'.xml',caption=caption,reference=source,check=check))

def knot_art(name,s,note):
    a=Art(f'{name}  /  {s}',note); a.legend(name=='Square knot'); return a

def bowline(s):
    a=knot_art('Bowline',s,['Small loop: standing part crosses on top.','Bring the end up through the small loop.','Collar goes behind the standing part.','Return down through the same small loop.','Dress the knot; keep the tail inside the large loop.'][s-1])
    # Standing part, nipping loop, then large fixed loop form one continuous rope.
    a.rope('M330 100 L330 190 C330 235 265 255 250 219 C227 165 310 147 347 208 L440 306 C479 354 448 380 378 375 C290 370 252 336 278 289',TAN)
    a.rope('M330 169 L330 190',TAN,gap=True)
    if s==1:
        a.rope('M278 289 C289 270 307 270 312 292 L315 322',RED)
        a.arrow('M380 240 C410 210 393 173 367 175',367,175,175)
        a.text('small loop',80,175); a.line(173,173,242,186)
    else:
        a.rope('M278 289 C278 260 284 223 292 197 C299 171 310 154 318 143',RED)
        if s>=3:
            a.rope('M318 143 C338 122 365 130 367 147 C368 159 351 165 335 164',RED)
            a.rope('M330 130 L330 166',TAN,gap=True) # collar behind standing part
            a.rope('M335 164 C318 166 317 183 318 211',RED)
        if s>=4:
            a.rope('M318 211 C319 237 332 257 341 280',RED)
        if s==2: a.arrow('M305 152 C347 106 385 114 393 149',393,149,85)
        if s==3: a.arrow('M373 186 C362 221 352 239 346 253',346,253,115)
        if s==4: a.arrow('M374 289 L374 320',374,320,90)
        if s==5:
            a.arrow('M330 125 L330 87',330,87,-90)
            a.text('tail inside loop',455,271); a.line(450,267,345,280)
        # Both legs of the working-end bight go underneath the front/lower rim
        # and above its upper rim, as in the reference's nipping loop.
        a.rope(cubic_section([(330,190),(330,235),(265,255),(250,219)],.17,.73),TAN,gap=True)
    a.text('standing part',415,111); a.line(409,108,342,110)
    return a

def sheet(s):
    a=knot_art('Sheet bend',s,['Form a bight in the thicker rope.','Enter the bight from below.','Go behind BOTH legs of the bight.','Tuck under the red rope only.','Both short ends finish on the same side.'][s-1])
    a.rope('M220 125 L365 165 C445 180 445 260 365 276 L220 315',TAN,16)
    if s>=2: a.rope('M340 360 L340 240 L350 203',RED)
    if s>=3:
        a.rope('M350 203 C337 190 330 158 354 134 C399 100 468 144 464 234 C462 296 400 321 365 294',RED)
        a.rope('M320 155 L369 166',TAN,16,True)
        a.rope('M330 286 L371 275',TAN,16,True)
    if s>=4:
        a.rope('M365 294 C347 284 333 245 313 245 L247 245',RED)
        a.rope('M340 249 L340 269',RED,gap=True)
    if s==1: a.arrow('M310 370 L310 242',310,242,-90)
    if s==2: a.arrow('M382 217 C410 205 439 170 430 151',430,151,-110)
    if s==3: a.arrow('M373 305 C345 317 313 305 309 282',309,282,-90)
    if s>=4:
        a.text('under itself',85,235); a.line(189,237,331,262)
        a.text('thicker rope',490,204); a.line(485,207,428,208)
    if s>=2: a.rope('M326 286.490 L358 277.883',TAN,16,True)
    return a

def square(s):
    a=knot_art('Square knot',s,['One end in each hand.','Right over left, then under.','Reverse: left over right, then under.','Two interlocking bights; paired ends on each side.'][s-1])
    if s==1:
        a.rope('M145 330 C220 280 245 192 280 120',TAN)
        a.rope('M575 330 C500 280 475 192 440 120',RED)
        a.arrow('M475 147 C430 115 340 117 299 154',299,154,150)
    elif s==2:
        a.rope('M155 340 L340 235 C394 206 418 160 470 125',TAN)
        a.rope('M565 340 L373 237 C314 206 301 165 250 125',RED)
        a.rope('M334 216 C353 230 368 238 395 251',RED,gap=True)
        a.text('red crosses over tan',380,174,16)
        a.arrow('M276 147 C236 185 258 213 309 229',309,229,20)
    else:
        # Open reef layout: each tail returns beside its own standing part.
        a.rope('M100 260 H305 C353 260 387 300 416 285 C446 269 446 236 419 229 C385 219 348 254 329 230 L263 145',TAN)
        a.rope('M620 260 H430 C383 260 330 303 315 271 C290 233 315 205 347 215 C393 244 430 233 465 145',RED)
        a.rope('M310 257 C311 267 316 277 324 283',RED,gap=True)
        a.rope('M340 241 L317 215',TAN,gap=True)
        a.rope('M394 234 C413 230 426 220 437 200',RED,gap=True)
        a.rope('M380 282 C394 293 407 292 416 285',TAN,gap=True)
        if s==3:
            a.arrow('M443 126 C400 101 326 103 282 142',282,142,140)
            a.text('second half-knot reverses the first',178,345,18)
        else:
            a.arrow('M150 230 H85',85,230,180)
            a.arrow('M570 230 H635',635,230,0)
            a.text('tan pair',143,335); a.text('red pair',510,335)
    return a

def figure(s):
    a=knot_art('Figure eight',s,['Cross the working end over the standing part.','Pass behind the standing part.','Return through the original loop.','Dress the two lobes to form an eight.'][s-1])
    a.rope('M280 110 L348 235 C402 325 469 307 468 258 C468 213 423 189 378 206 L310 235',TAN)
    a.rope('M378 206 L310 235 L270 250',RED,gap=True)
    if s>=2:
        a.rope('M270 250 C221 254 204 177 257 147 C308 117 369 143 382 190',RED)
        a.rope('M290 128.382 L309 163.309',TAN,gap=True)
    if s>=3:
        a.rope('M382 190 C394 230 399 272 377 295 L351 322',RED)
        a.rope(cubic_section([(348,235),(402,325),(469,307),(468,258)],.14,.34),TAN,gap=True)
    if s==1: a.arrow('M242 265 C205 246 204 199 225 183',225,183,-45)
    if s==2: a.arrow('M407 170 C436 202 444 239 425 260',425,260,130)
    if s==3: a.text('through original loop',457,352,16)
    if s==4: a.arrow('M268 99 L239 73',239,73,-135); a.arrow('M352 335 L334 361',334,361,125)
    return a

def clove_on(a,x,y,stage=3,w=90,scale=1):
    # Back sections first, occluded by solid pole; front turns and diagonal last.
    a.rope(f'M{x+w} {y-22} C{x+w+28} {y-17} {x+w+28} {y+5} {x+w} {y+10} L{x} {y+10}',RED,8)
    if stage>=2: a.rope(f'M{x+w} {y+50} C{x+w+28} {y+55} {x+w+28} {y+65} {x+w} {y+70} L{x} {y+70}',RED,8)
    a.pole(x,y-65,w,170)
    a.rope(f'M{x-35} {y-38} L{x} {y-22} L{x+w} {y-22}',TAN,8)
    if stage>=2:
        a.rope(f'M{x} {y+10} L{x+w} {y+50}',RED,8)
        a.rope(f'M{x} {y+70} C{x-16} {y+68} {x-16} {y+54} {x} {y+52} L{x+w} {y+52}',RED,8)
        if stage==2: a.rope(f'M{x+w} {y+52} L{x+w+45} {y+73}',RED,8)
    else: a.rope(f'M{x} {y+10} C{x-22} {y+13} {x-27} {y+34} {x-40} {y+45}',RED,8)
    if stage>=3:
        a.rope(f'M{x+w} {y+52} C{x+w+18} {y+45} {x+w-4} {y+32} {x+w/2} {y+27} L{x+w+54} {y-9}',RED,8)
        a.rope(f'M{x+w/2-17} {y+24} L{x+w/2+17} {y+36}',RED,8,True)

def clove(s):
    a=knot_art('Clove hitch',s,['One complete turn around the pole.','Cross the first turn; wrap again.','Tuck the end beneath the diagonal bridge.','Pull the ends in opposite directions.'][s-1])
    clove_on(a,300,215,min(s,3),115)
    a.text('back turns hidden by pole',430,348,15)
    if s==1: a.arrow('M304 251 C271 256 248 275 259 300',259,300,65)
    if s==2: a.arrow('M448 241 C467 215 449 194 420 199',420,199,170)
    if s>=3: a.text('under diagonal',70,248); a.line(195,244,318,223)
    if s==4: a.arrow('M250 166 L214 146',214,146,-150); a.arrow('M475 206 L511 186',511,186,-30)
    return a

def hitch(s,taut=False):
    name='Taut-line hitch' if taut else 'Two half hitches'
    notes=(['Loop around the stake; leave a long working end.','Two turns inside the loop, toward the stake.','One half hitch outside the loop.','Dress snugly; slide to adjust, then test under tension.'] if taut else
           ['Take the working end around the post.','Over the standing part, around it, through the loop.','Repeat in the SAME direction.','Slide both hitches toward the post and dress snugly.'])
    a=knot_art(name,s,notes[s-1]); a.pole(130,142,60,177)
    a.rope('M620 150 L200 150 C90 150 85 305 205 305 L327 272',TAN)
    a.rope('M327 272 C342 254 343 226 343 209',RED)
    if s>=2:
        # Upper and lower front arcs are separated by the rear half-turn behind tan.
        count=0 if taut else 1
        for i in range(count):
            x=343-i*38
            a.rope(f'M{x} 209 C{x-28} 204 {x-30} 163 {x-14} 137 C{x+1} 110 {x+32} 120 {x+31} 146',RED)
            a.rope(f'M{x-28} 150 L{x+8} 150',TAN,gap=True)
            a.rope(f'M{x+31} 146 C{x+32} 167 {x+11} 173 {x-5} 183',RED)
            a.rope(f'M{x-5} 183 C{x-21} 193 {x-31} 184 {x-30} 163',RED)
        if taut:
            points=[]
            for i in range(97):
                t=i/96*4*math.pi
                points.append((343-76*t/(4*math.pi),150+40*math.sin(t+math.pi/2)))
            a.rope('M343 209 L343 190 '+' '.join(f'L{x:.2f} {y:.2f}' for x,y in points),RED)
            for x in [333.5,295.5]: a.rope(f'M{x-13} 150 H{x+13}',TAN,gap=True)
            a.rope('M267 190 C300 244 389 215 425 199',RED)
        else: a.rope('M313 163 C314 200 380 220 425 199',RED)
    if s>=3:
        x=425
        a.rope(f'M{x} 199 C{x-20} 189 {x-28} 160 {x-14} 137 C{x+1} 110 {x+32} 120 {x+31} 146',RED)
        a.rope(f'M{x-28} 150 L{x+8} 150',TAN,gap=True)
        a.rope(f'M{x+31} 146 C{x+32} 167 {x+11} 175 {x-5} 183 L{x-25} 218',RED)
        a.rope(f'M{x-22} 171 C{x-20} 183 {x-17} 193 {x-10} 200',RED,gap=True)
    if s==1: a.arrow('M364 210 C418 197 418 130 378 113',378,113,-155)
    if s==2: a.arrow('M450 223 C499 192 499 135 477 120',477,120,-130)
    if s==4: a.arrow('M448 255 H290',290,255,180)
    a.text('standing part',491,108); a.line(536,114,536,139)
    if taut: a.text('inside: toward stake',125,364,17); a.text('outside: away from stake',414,364,17)
    return a

def timber(s):
    a=knot_art('Timber hitch',s,['Take the rope around the log.','Pass the working end around the standing part.','At least three turns around its OWN returning leg.','Pull the standing part; keep tension on the hitch.'][s-1])
    # One continuous noose round a horizontal log; working end returns around its own leg.
    a.rope('M125 255 H330 C391 255 437 295 424 321 C409 353 421 144 383 137',TAN)
    a.pole(170,187,420,85)
    a.rope('M125 255 H330 C367 256 383 297 412 315',TAN)
    a.rope('M383 137 L371 181',RED)
    if s>=2:
        a.rope('M371 181 L342 254 C330 284 308 268 321 249 C333 231 353 244 367 258 L411 174',RED)
        a.rope('M327 255 H351',TAN,gap=True)
    if s>=3:
        for x,y in [(375,242),(387,217),(399,192)]:
            a.rope(f'M{x} {y} c-18 -16 -27 2 -15 12 c17 16 31 -3 21 -12',RED,7,True)
        a.rope('M411 174 L485 173',RED,8)
        a.text('three turns around own leg',363,366,17); a.line(422,338,387,229)
    if s==1: a.arrow('M350 156 C306 172 300 214 308 235',308,235,70)
    if s==2: a.arrow('M393 302 C440 289 461 244 457 213',457,213,-90)
    if s==4: a.arrow('M143 254 H84',84,254,180)
    return a

def whipping(s):
    a=knot_art('Common whipping',s,['Lay a twine bight along the rope, toward its end.','Wrap toward the rope end; cover the bight.','Pass the finishing end through the exposed bight.','Pull the starting end to bury the bight under wraps.'][s-1])
    a.rope('M95 243 H605',TAN,48)
    if s==4:
        a.rope('M330 205 H425 C450 205 450 278 425 278 H348',RED,5)
    else: a.rope('M252 205 H492 C557 205 557 278 492 278 H300',RED,5)
    if s>=2:
        for x in range(370,482,14):
            a.rope(f'M{x} 219 C{x-7} 230 {x-7} 255 {x} 267',RED,8)
        if s==4: a.rope('M482 267 L518 283',RED,6)
        else: a.rope('M482 267 L568 304',RED,6)
    if s==3: a.rope('M568 304 C595 300 578 234 538 237 L516 237',RED,6)
    if s==4: a.arrow('M302 202 H230',230,202,180)
    if s==1: a.arrow('M324 180 H446',446,180,0)
    if s==2: a.text('width about rope diameter',278,340,18)
    if s==3: a.arrow('M540 173 L540 213',540,213,90)
    return a

def lashing(s,kind):
    titles={'square':'Square lashing','diagonal':'Diagonal lashing','shear':'Shear lashing'}
    notes={'square':['Start: clove hitch below the crosspiece.','3 wraps: OVER the crosspiece, BEHIND the upright.','2 fraps: between the poles, around the wraps.','Finish: clove hitch on the crosspiece.'],
           'diagonal':['Start: timber hitch around BOTH crossing poles.','3 turns around the first pair of opposite angles.','3 turns across the other pair of angles.','2 fraps: between the poles, around both sets.','Finish: clove hitch around ONE pole.'],
           'shear':['Start: clove hitch on one of two parallel poles.','7 loose wraps around both poles.','2 tight fraps between the poles, around the wraps.','Finish: clove hitch on the other pole.','Spread the lower ends to form shear legs.']}
    a=knot_art(titles[kind],s,notes[kind][s-1])
    if kind=='shear':
        if s==5:
            a.path('M340 111 L370 118 L240 370 L208 361 Z',WOOD,INK,2)
            a.path('M360 111 L330 118 L462 370 L494 361 Z',WOOD,INK,2)
        else: a.pole(278,112,62,256); a.pole(356,112,62,256)
        if s>=2:
            for i in range(7): a.rope(f'M276 {169+i*16} C308 {177+i*16} 389 {177+i*16} 420 {169+i*16}',TAN,8)
        if s>=3:
            for x in [343,352]: a.rope(f'M{x} 164 C{x-14} 190 {x-14} 252 {x} 276',RED,7,True)
        if s<=1: clove_on(a,278,145,3,62)
        if s==4:
            a.text('finish on other pole',32,122,15)
            clove_on(a,70,205,3,62)
            a.arrow('M150 297 L252 263',252,263,-18)
        if s==5: a.arrow('M240 327 L177 364',177,364,150); a.arrow('M461 327 L524 364',524,364,30)
    elif kind=='square':
        # Rope back portions are masked by the upright. Front rope goes over crosspiece.
        if s>=2:
            for i in range(3):
                l=284-i*12; r=436+i*12; top=157+i*10; bottom=306-i*10
                a.rope(f'M{l} {top} H{r} V{bottom} H{l} Z',TAN,8)
        a.pole(330,101,60,265)
        a.pole(172,207,376,60)
        if s>=2:
            for i in range(3):
                l=284-i*12; r=436+i*12; top=157+i*10; bottom=306-i*10
                a.rope(f'M{l} {top} V{bottom} M{r} {top} V{bottom}',TAN,8)
        if s==1: clove_on(a,330,314,3,60)
        if s>=3:
            # Red encircles the rope bundles in the gap behind the crosspiece.
            # Only the portions above and below its edges are visible from the front.
            for x in [0,11]:
                a.rope(f'M{246+x} 196 C{258+x} 183 {305+x} 183 {318+x} 196 M{246+x} 278 C{258+x} 291 {305+x} 291 {318+x} 278',RED,7,True)
                a.rope(f'M{402-x} 196 C{415-x} 183 {458-x} 183 {471-x} 196 M{402-x} 278 C{415-x} 291 {458-x} 291 {471-x} 278',RED,7,True)
            a.text('fraps behind crosspiece',438,345,15); a.line(479,320,453,278)
        if s==4:
            # Inset illustrates the finishing hitch separately, avoiding occlusion.
            a.text('finish on crosspiece',31,121,15)
            clove_on(a,64,208,3,60)
            a.arrow('M145 279 L192 245',192,245,-35)
    else:
        a.path('M191 115 L225 96 L527 350 L493 372 Z',WOOD,INK,2)
        a.path('M506 97 L539 122 L221 371 L189 348 Z',WOOD,INK,2)
        if s==1:
            a.rope('M288 183 C245 217 278 296 352 290 C424 286 459 218 412 184 C386 158 313 164 288 183',TAN,10)
            a.rope('M288 183 C260 155 232 191 273 205',RED,9)
            for i in range(3): a.rope(f'M{277+i*16} {201-i*5} c-10 -17 -23 -4 -13 8',RED,6,True)
        if s>=2:
            for i in range(3):
                x=i*12
                a.rope(f'M{279+x} {173-x} C{246+x} {192-x} {377+x} {310-x} {419+x} {288-x}',TAN,8)
        if s>=3:
            for i in range(3):
                x=i*12
                a.rope(f'M{430-x} {181-x} C{464-x} {210-x} {314-x} {324-x} {280-x} {286-x}',RED,8,True)
        if s>=4:
            a.rope('M286 241 C302 287 410 290 434 244 M289 229 C314 275 404 277 430 232',RED,7,True)
        if s==5:
            a.text('finish on one pole',32,122,15); clove_on(a,64,209,3,60)
    return a

references={
'rope-care':('common-whipping-knot','Twine bight points to end; bury it under wraps.'),
'square-knot':('square-knot','Reversed half-knots, alternating crossings, paired same-rope ends.'),
'two-half-hitches':('two-half-hitches-knot','Both half hitches turn in the same direction around standing part.'),
'taut-line':('https://filestore.scouting.org/filestore/training/pdf/510-033(17)BALOO.pdf#page=98','Scout taut-line: two inside wraps toward stake, one outside half hitch; not the midshipman variant.'),
'bowline':('bowline-knot','Nipping loop, collar behind standing part, returning tail inside large loop.'),
'clove-hitch':('clove-hitch-knot-rope-end','Two pole turns, crossed bridge, end tucked beneath bridge.'),
'timber-hitch':('timber-hitch-knot','End encircles standing part then twists around its own leg at least three times.'),
'sheet-bend':('sheet-bend-knot','Bight in thick rope; thin rope behind both legs then under itself; tails same side.'),
'figure-eight':('figure-8-knot','End around standing part then through initial loop; three alternating crossings.'),
'square-lashing':('https://www.animatedknots.com/square-lashing-knot','Start below crosspiece, over/behind wraps, fraps in gap; guide uses the traditional crosspiece clove finish.'),
'diagonal-lashing':('diagonal-lashing-knot','Timber hitch around both poles, both wrap directions, fraps, single pole clove finish.'),
'shear-lashing':('shear-lashing-knot','Clove on one pole, wraps around both, fraps between, clove on other, spread legs.')}

def knots():
    functions={'rope-care':(whipping,4),'square-knot':(square,4),'two-half-hitches':(lambda s:hitch(s),4),'taut-line':(lambda s:hitch(s,True),4),'bowline':(bowline,5),'clove-hitch':(clove,4),'timber-hitch':(timber,4),'sheet-bend':(sheet,5),'figure-eight':(figure,4),'square-lashing':(lambda s:lashing(s,'square'),4),'diagonal-lashing':(lambda s:lashing(s,'diagonal'),5),'shear-lashing':(lambda s:lashing(s,'shear'),5)}
    for key,(fn,count) in functions.items():
        ref,check=references[key]
        if not ref.startswith('http'): ref='https://www.animatedknots.com/'+ref
        for s in range(1,count+1): fn(s).save(f'knots/{key}-{s}',source=ref,check=check)

if __name__=='__main__':
    knots()
    (ROOT/'art/vector-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print(f'Wrote {len(manifest)} original vector illustrations.')
