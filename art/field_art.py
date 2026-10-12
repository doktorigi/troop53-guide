"""Original field-guide figures, using the same SVG -> native Android path exporter."""
from vector_art import *

def tent(a,x,y,scale=1):
    a.path(f'M{x} {y} l{85*scale} {-100*scale} l{105*scale} {100*scale} Z',GREEN,INK,2)
    a.path(f'M{x+85*scale} {y-100*scale} l{-47*scale} {100*scale} h{91*scale} Z',TAN,INK,2)
    a.line(x-18*scale,y+8*scale,x+85*scale,y-100*scale,RED,2)
    a.line(x+85*scale,y-100*scale,x+211*scale,y+8*scale,RED,2)

def tree(a,x,y,s=1):
    a.rect(x-5*s,y,10*s,35*s,WOOD)
    a.path(f'M{x} {y-95*s} l{-43*s} {83*s} h{25*s} l{-37*s} {28*s} h{110*s} l{-37*s} {-28*s} h{25*s} Z',GREEN)

def kit(a,x,y):
    a.rect(x,y,126,92,TAN,INK); a.rect(x+40,y-14,45,14,TAN,INK)
    a.rect(x+49,y+22,28,50,RED); a.rect(x+38,y+33,50,28,RED)

def person(a,x,y,s=1,skin='#B98060'):
    a.circle(x,y,15*s,skin,INK)
    a.path(f'M{x-16*s} {y+20*s} h{32*s} l{12*s} {64*s} h{-56*s} Z',GREEN,INK,2)
    a.line(x-10*s,y+87*s,x-18*s,y+130*s,INK,9*s)
    a.line(x+10*s,y+87*s,x+18*s,y+130*s,INK,9*s)
    a.line(x-19*s,y+128*s,x-31*s,y+130*s,INK,9*s)
    a.line(x+19*s,y+128*s,x+31*s,y+130*s,INK,9*s)

def headers():
    for key,title in [('basics','A promise to help'),('firstaid','Ready to care'),('camping','A home on the trail'),('cooking','The patrol kitchen'),('fire','Build small. Leave cold.'),('knots','Learn by doing'),('navigation','Find your way'),('hiking','One trail. Two buddies.'),('survival','Stop. Think. Observe. Plan.'),('nature','Notice the world'),('water','Safety comes first'),('tools','Care for your tools'),('lnt','Leave the place better'),('flag','Service and citizenship'),('prepared','Ready for the unexpected')]:
        a=Art(); a.circle(594,102,32,TAN)
        a.path('M0 314 L147 222 L234 268 L389 165 L535 287 L720 230 V450 H0 Z',LIGHT)
        a.path('M0 353 Q165 324 320 357 T720 340 V450 H0 Z','#E7DEC9')
        tree(a,79,305,.9); tree(a,640,316,.7)
        if key=='camping': tent(a,260,325,1.1)
        elif key in ['firstaid','prepared']: kit(a,302,227)
        elif key=='cooking':
            a.rect(235,307,240,12,WOOD); a.rect(262,319,12,57,WOOD); a.rect(447,319,12,57,WOOD)
            a.path('M297 211 H418 L405 295 H310 Z',GREEN,INK,3); a.rect(290,205,139,8,INK)
            a.path('M305 229 C253 208 253 280 311 271',stroke=INK,width=5)
        elif key=='fire':
            a.circle(362,291,57,LIGHT,WOOD)
            a.rope('M300 325 L421 275 M315 275 L410 325',WOOD,13)
            a.path('M330 286 Q320 230 360 193 Q355 239 383 249 Q411 227 397 281 Z',RED)
        elif key=='knots':
            a.rope('M229 281 C216 189 360 148 397 221 C435 302 285 318 313 233 C335 169 438 198 487 277',TAN,17)
            a.rope('M333 212 C350 191 367 191 385 204',RED,17,True)
        elif key=='navigation':
            a.rect(273,140,154,223,TAN,INK); a.circle(350,254,63,PAPER,GREEN)
            a.path('M350 198 L335 254 H366 Z',RED); a.path('M350 310 L335 254 H366 Z',GREEN)
            a.arrow('M350 195 V154',350,154,-90,GREEN)
        elif key=='hiking':
            a.path('M276 450 Q401 340 337 300 Q289 265 378 202',stroke=TAN,width=24)
            person(a,289,249,.65); person(a,425,260,.65,'#E0B797')
        elif key=='survival': tent(a,267,335,1); a.rect(283,273,47,24,TAN)
        elif key=='nature':
            a.line(350,328,350,204,GREEN,5)
            a.path('M350 276 C244 273 274 169 350 248 Z',GREEN)
            a.path('M350 237 C441 224 405 140 350 206 Z',GREEN)
            a.path('M486 169 Q511 152 529 177 Q549 148 568 165',stroke=INK,width=3)
        elif key=='water':
            for y in [312,331,350]: a.path(f'M198 {y} Q232 {y-13} 268 {y} T409 {y} T541 {y}',stroke=BLUE,width=4)
            a.circle(359,244,61,RED,INK); a.circle(359,244,35,PAPER)
        elif key=='tools':
            a.path('M250 239 H467 L429 297 H250 Z',TAN,INK,3)
            a.circle(477,263,29,GREEN,INK); a.circle(477,263,15,PAPER)
            for x in range(260,425,17): a.path(f'M{x} 295 l8 12 l8 -12',stroke=INK,width=2)
        elif key=='lnt':
            a.path('M317 324 C280 310 277 214 360 186 C380 275 374 306 317 324 Z',GREEN)
            a.line(305,339,357,220,TAN,3)
            a.text('PACK IT IN',260,366,19); a.text('PACK IT OUT',252,391,19)
        elif key=='flag':
            a.line(309,353,309,148,INK,6)
            a.rect(312,153,186,98,PAPER,INK)
            for row in range(0,13,2): a.rect(312,153+row*98/13,186,98/13,RED)
            a.rect(312,153,74.4,98*7/13,BLUE)
            for row in range(9):
                count=6 if row%2==0 else 5
                for col in range(count): a.star(312+(col+(.5 if count==6 else 1))*74.4/6,153+(row+1)*98*7/130,2,PAPER)
        else: person(a,321,205,.9); person(a,426,222,.75,'#E0B797')
        a.text(title,28,417,22,GREEN)
        a.save('headers/'+key)

def compass(aligned=False):
    a=Art('Put red in the shed' if aligned else 'Know your baseplate compass')
    a.rect(242,89,222,283,'#E9E4D6',INK)
    a.arrow('M353 168 V106',353,106,-90,GREEN)
    a.circle(353,263,90,PAPER,INK)
    for x in [320,336,370,386]: a.line(x,206,x,320,TAN,2)
    a.path('M342 280 V231 H334 L353 200 L372 231 H364 V280 Z',stroke=RED,width=3)
    a.path('M353 209 L342 263 H364 Z',RED)
    a.path('M353 315 L342 263 H364 Z',INK)
    a.circle(353,263,5,WOOD)
    for label,x,y in [('N / 0',332,188),('E / 90',441,267),('S / 180',325,362),('W / 270',218,267)]: a.text(label,x,y,14)
    for y in range(104,359,14): a.line(245,y,253 if y%28 else 260,y,INK,1)
    for text,x,y,u,v in [('travel arrow',43,129,344,135),('rotating housing',493,183,424,207),('red: magnetic north',461,310,365,233),('orienting arrow',41,349,340,278),('baseplate + ruler',41,218,246,217)]:
        a.text(text,x,y,15); a.line(x+20,y+5,u,v,INK,1)
    a.text('orienting lines',496,358,15); a.line(496,361,387,300,INK,1)
    if aligned: a.text('Turn your whole body until the red needle sits in the outline.',44,415,18,GREEN)
    else: a.text('Hold it level; keep it away from metal and electronics.',82,415,18,GREEN)
    return a

def topo():
    a=Art('Read the shape of the land','Contour interval in this example: 20 feet.')
    for r,label in [(127,'100'),(95,'120'),(63,'140'),(30,'160')]:
        a.path(f'M{190-r} 246 a{r} {r*.7} 0 1 0 {r*2} 0 a{r} {r*.7} 0 1 0 {-r*2} 0',stroke='#7A5A2E',width=2)
        a.text(label,190+r-20,248,13,'#7A5A2E')
    for y,label in [(183,'160'),(223,'140'),(263,'120'),(303,'100')]:
        a.path(f'M421 {y+42} L535 {y-23} L647 {y+42}',stroke='#7A5A2E',width=2)
        a.text(label,652,y+47,14,'#7A5A2E')
    a.arrow('M535 153 V361',535,361,90,BLUE)
    a.text('Hill: elevation increases inward.',56,377,16)
    a.text('Valley: the Vs point upstream.',410,377,16)
    a.text('Stream flows downhill.',449,402,16,BLUE)
    return a

def polaris():
    a=Art('The Big Dipper points to Polaris','Northern Hemisphere; the pattern rotates through the night.')
    # Seven stars, with the bowl open on the side of the outward pointer extension.
    points=[(98,279),(154,248),(215,260),(279,297),(284,347),(362,350),(366,305)]
    a.path('M98 279 L154 248 L215 260 L279 297 L284 347 L362 350 L366 305 L279 297',stroke=BLUE,width=2)
    for x,y in points: a.circle(x,y,5,BLUE)
    # Merak -> Dubhe separation = 45, continue ~5 x upward = 225.
    a.arrow('M362 350 L366 305 L386 80',386,80,-85,RED)
    a.circle(386,80,7,RED)
    a.text('Polaris / North Star',412,110,19,GREEN)
    a.text('Dubhe',393,310,16); a.text('Merak',394,353,16)
    a.text('About 5 times the pointer-star spacing',405,200,15)
    a.text('Big Dipper',135,393,20,GREEN)
    return a

def shadow():
    a=Art('Shadow stick: an approximate east-west line','Mark the first shadow tip; wait 15–20 minutes, then mark again.')
    a.path('M72 310 L336 168 L658 283 L384 385 Z',LIGHT,INK,1)
    a.line(337,256,337,115,WOOD,8)
    a.line(337,256,205,310,'#9E9787',11); a.line(337,256,269,317,'#C2BBAA',11)
    a.circle(205,310,8,INK); a.circle(269,317,8,INK)
    a.arrow('M174 307 L306 321',306,321,6,RED)
    a.text('FIRST / west',104,370,18); a.line(178,351,205,320)
    a.text('SECOND / east',340,343,18); a.line(338,336,278,321)
    a.text('Not a precise compass bearing; use map and compass when possible.',35,416,17)
    return a

def firelays():
    a=Art('Four useful fire lays','Tinder in the center; kindling around it. Drawings show structure before lighting.')
    for name,x,y in [('Teepee',166,215),('Log cabin',528,215),('Lean-to',166,366),('Star (top view)',528,340)]:
        a.text(name,x-70,414 if name.startswith('Star') else y+44,19,GREEN)
        a.circle(x,y-13,16,TAN,WOOD)
        if name=='Teepee':
            for dx in [-55,-30,25,53]: a.rope(f'M{x+dx} {y+14} L{x} {y-98}',WOOD,8)
        elif name=='Log cabin':
            for i in range(3):
                yy=y-i*14
                a.rope(f'M{x-60} {yy} H{x+60} M{x-60} {yy-63} H{x+60}',WOOD,7)
                a.rope(f'M{x-42} {yy+9} V{yy-74} M{x+42} {yy+9} V{yy-74}',WOOD,7)
        elif name=='Lean-to':
            a.rope(f'M{x-80} {y-57} H{x+67}',WOOD,22)
            for xx in range(-40,50,20): a.rope(f'M{x+xx} {y-62} L{x+xx+22} {y+8}',WOOD,7)
        else:
            for angle in [0,72,144,216,288]:
                t=math.radians(angle); a.rope(f'M{x+math.cos(t)*22} {y+math.sin(t)*22-20} L{x+math.cos(t)*60} {y+math.sin(t)*60-20}',WOOD,10)
    return a

def fireout():
    a=Art('Drown. Stir. Feel. Repeat.','Water and mixing reach hidden embers. Leave only when completely cold.')
    for x in [140,358,576]:
        a.path(f'M{x-78} 315 Q{x} 355 {x+78} 315 L{x+60} 274 H{x-60} Z',LIGHT,WOOD,2)
        for dx in [-40,-20,0,20,40]: a.circle(x+dx,306,5,'#756C5C')
    a.path('M62 167 L141 157 L153 224 L91 242 Z',BLUE,INK,2)
    for x in [132,150,164]: a.path(f'M{x} 227 Q{x+15} 256 {x+10} 287',stroke=BLUE,width=3)
    a.line(382,157,335,316,WOOD,9)
    a.arrow('M395 247 C418 277 408 308 386 314',386,314,170)
    a.path('M524 203 L543 165 Q549 156 555 169 L553 194 L583 175 Q604 166 610 182 L629 205 Q630 220 613 226 L563 230 Z','#D5A889',INK,2)
    a.text('hover near; do not touch',491,258,13)
    for title,x in [('DROWN',95),('STIR',328),('FEEL',552)]: a.text(title,x,376,19,GREEN)
    return a

def cpr():
    a=Art('CPR: hands in the center of the chest','Adult / teen: heel of one hand, other hand on top. Shoulders over hands.')
    a.line(103,351,635,351,INK,3)
    a.circle(129,318,24,'#B98060',INK)
    a.path('M163 296 H373 L423 323 H596 V347 H165 Z',GREEN,INK,2)
    a.path('M296 114 L348 119 L321 211 L292 226 L272 176 Z',TAN,INK,2)
    a.circle(335,89,22,'#E0B797',INK)
    a.path('M301 215 L305 300 L283 302 L278 217 Z','#E0B797',INK,2)
    a.path('M326 210 L321 297 L300 299 L304 211 Z','#E0B797',INK,2)
    a.rect(280,297,43,13,'#E0B797',INK)
    a.line(295,126,295,290,RED,2)
    a.arrow('M365 222 V291',365,291,90)
    a.text('straight elbows',427,151,18); a.line(425,158,323,209)
    a.text('center of chest',427,269,18); a.line(424,277,319,301)
    a.text('100–120 compressions / minute. Let the chest recoil.',99,408,18,GREEN)
    return a

def tick():
    a=Art('Tick removal: grip close to the skin','Clean fine-tipped tweezers; steady, even upward pressure. Do not twist.')
    a.rect(93,302,537,42,'#D5A889')
    a.path('M311 302 C295 295 302 259 325 256 C350 258 358 286 337 302 Z',WOOD,INK,2)
    for y in [270,279,288,297]:
        a.path(f'M308 {y} l-20 -8 l-9 10 M340 {y} l20 -8 l9 10',stroke=INK,width=2)
    a.path('M426 123 L333 301 L323 300 L406 125 Z',LIGHT,INK,2)
    a.path('M453 133 L340 302 L332 299 L438 127 Z',LIGHT,INK,2)
    a.arrow('M354 232 L395 149',395,149,-65)
    a.text('grip mouthparts,',89,195,18); a.text('not the swollen body',89,219,18); a.line(245,227,326,298)
    a.text('skin surface',472,369,17)
    return a

def sling():
    a=Art('Support an injured forearm in a sling','Support as found. Keep the fingers visible; check warmth, color, and feeling.')
    a.circle(359,117,38,'#B98060',INK)
    a.path('M312 159 L405 159 L458 352 H256 Z',GREEN,INK,2)
    a.path('M294 199 L285 308 L406 303 L409 280 L312 280 L319 207 Z','#B98060',INK,2)
    a.path('M284 311 L323 161 L399 168 L418 294 Z',TAN,INK,2)
    a.path('M284 311 L410 285 L400 310 Z',LIGHT,INK,2)
    a.path('M405 282 L442 283 L442 303 L403 303 Z','#B98060',INK,2)
    a.text('fingers exposed',476,265,17); a.line(478,272,438,286)
    a.text('tie beside neck; pad the knot',130,396,18)
    return a

def splint():
    a=Art('A padded forearm splint','Support in the position found; do not straighten. Secure away from the injury.')
    a.path('M118 180 L171 274 L524 274 L566 250 L606 259 L599 296 L565 314 H149 L78 203 Z','#D5A889',INK,2)
    a.rect(133,315,435,16,WOOD,INK); a.rect(131,303,437,12,LIGHT)
    for x in [165,262,459]: a.rect(x,270,17,65,TAN,INK)
    a.text('padding',323,369,17); a.line(335,350,335,312)
    a.text('past elbow',49,389,16); a.line(143,371,139,326)
    a.text('past wrist',510,389,16); a.line(550,371,562,326)
    a.text('Leave fingers visible. Recheck circulation after securing.',108,425,17,GREEN)
    return a

def recovery():
    a=Art('Recovery position: on the side','Only if breathing normally; keep checking breathing and follow emergency guidance.')
    a.line(92,337,626,337,INK,3)
    a.circle(154,267,25,'#B98060',INK)
    a.path('M184 247 L340 247 L357 292 L191 302 Z',GREEN,INK,2)
    a.path('M205 260 L185 295 L143 293 L142 280 L171 279 L185 254 Z','#B98060',INK,2)
    a.path('M338 278 L474 320 L600 322',stroke=GREEN,width=28)
    a.path('M340 278 L442 218 L510 320',stroke=TAN,width=25)
    a.line(601,323,623,323,INK,9); a.line(510,323,534,323,INK,9)
    a.text('hand supports cheek',55,190,18); a.line(151,199,151,281)
    a.text('upper knee bent',435,174,18); a.line(496,183,444,216)
    a.text('Face angled downward; airway open.',151,402,18,GREEN)
    return a

def plants():
    a=Art('Poison ivy, oak, and sumac','Illustrative leaf patterns vary. Avoid touching unknown plants; never burn them.')
    def leaf(x,y,dx,dy,oak=False):
        if oak:
            d=f'M{x} {y} C{x-20} {y-20} {x-45} {y-22} {x-31} {y-37} C{x-48} {y-50} {x-26} {y-59} {x-12} {y-56} C{x-21} {y-80} {x+1} {y-85} {x+6} {y-108} C{x+16} {y-77} {x+41} {y-77} {x+25} {y-56} C{x+54} {y-52} {x+41} {y-30} {x+27} {y-30} C{x+42} {y-15} {x+15} {y-11} {x} {y} Z'
        else: d=f'M{x} {y} C{x-53} {y-18} {x-36} {y-81} {x+dx} {y+dy} C{x+45} {y-64} {x+45} {y-16} {x} {y} Z'
        a.path(d,GREEN,INK,1); a.line(x,y,x+dx,y+dy,TAN,1)
    for x,oak in [(130,False),(359,True)]:
        a.line(x,344,x,235,RED,3)
        a.line(x,283,x-51,271,RED,3); a.line(x,283,x+51,271,RED,3)
        leaf(x,235,3,-108,oak); leaf(x-51,285,3,-108,oak); leaf(x+51,285,3,-108,oak)
    # Sumac: 4 opposite pairs plus terminal leaflet = 9, smooth margins, red rachis.
    a.line(590,348,590,124,RED,3)
    for y in [294,253,212,171]:
        for sign in [-1,1]:
            a.path(f'M590 {y} C{590+sign*27} {y-43} {590+sign*60} {y-20} {590+sign*67} {y-41} C{590+sign*69} {y-8} {590+sign*28} {y+10} 590 {y} Z',GREEN,INK,1)
    a.path('M590 142 C567 125 575 98 590 84 C608 102 610 127 590 142 Z',GREEN,INK,1)
    for name,x in [('Poison ivy',74),('Poison oak',298),('Poison sumac',526)]: a.text(name,x,380,19,GREEN)
    a.text('3 leaflets; longer center stalk',29,410,14); a.text('3 lobed leaflets',289,410,14); a.text('7–13 smooth leaflets',513,410,14)
    return a

def shelter():
    a=Art('Debris hut: cross-section','A small air space, thick dry debris above, and insulation underneath.')
    a.path('M100 350 Q155 142 353 104 Q554 145 615 350 Z',TAN,INK,2)
    a.path('M191 349 L353 173 L514 349 Z',PAPER,INK,2)
    a.path('M190 349 L353 163 L517 349',stroke=WOOD,width=9)
    a.circle(353,164,10,WOOD,INK)
    for x in range(180,535,17): a.path(f'M{x} 351 l7 -17 l10 17',stroke=GREEN,width=4)
    a.path('M292 331 H412 Q433 316 417 304 H285 Z',GREEN,INK,2)
    a.circle(275,317,14,'#B98060',INK)
    a.text('ridgepole + ribs',471,147,17); a.line(471,152,361,166)
    a.text('at least 2 ft of dry debris',56,92,17); a.line(126,102,223,201)
    a.text('thick dry bedding',294,405,18); a.line(362,385,362,350)
    return a

def signals():
    a=Art('Ground-to-air signals','Build large, high-contrast symbols in an open place; at least 10 ft long.')
    a.path('M101 138 L186 301 L271 138',stroke=GREEN,width=15)
    a.path('M417 142 L590 303 M590 142 L417 303',stroke=RED,width=15)
    a.text('V / NEED HELP',89,356,23,GREEN)
    a.text('X / NEED MEDICAL HELP',376,356,22,RED)
    a.text('Whistle: 3 blasts, pause, repeat.',184,416,20)
    return a

def flag(s):
    notes=['Fold lengthwise: stripes over stars.','Fold lengthwise again: blue field outside.','Start at the striped end, away from the union.','Continue triangular folds toward the blue field.','Tuck the edge; only the union shows.']
    a=Art(f'Flag fold / {s}',notes[s-1])
    if s<=2:
        top=104 if s==1 else 174; height=272 if s==1 else 136
        a.rect(99,top,516,height,PAPER,INK)
        for row in range(0,13,2): a.rect(99,top+row*height/13,516,height/13,RED)
        a.rect(99,top,206.4,height*7/13,BLUE)
        for row in range(9):
            count=6 if row%2==0 else 5
            for col in range(count): a.star(99+(col+(.5 if count==6 else 1))*206.4/6,top+(row+1)*height*7/130,3.5 if s==1 else 2.5,PAPER)
        a.line(100,top+height/2,614,top+height/2,INK,1)
        a.arrow(f'M385 {top+height-12} C465 {top+height-40} 462 {top+height/2+24} 391 {top+height/2-20}',391,top+height/2-20,-160)
        a.text('two people keep the fabric supported',162,401,18)
    elif s<=4:
        a.rect(92,188,532,84,PAPER,INK); a.rect(92,188,199,84,BLUE)
        for y in range(188,272,12): a.rect(292,y,332,6,RED)
        for x in range(107,286,25):
            for y in range(201,266,17): a.circle(x,y,2,PAPER)
        a.path('M624 188 L540 188 L540 272 Z',TAN,INK,2)
        a.line(540,188,540,272,INK,2)
        a.arrow('M612 299 C588 328 529 305 509 273',509,273,-120)
        if s==4:
            for x in range(456,292,-84): a.line(x,188,x-84,272,INK,1)
            a.arrow('M524 342 H323',323,342,180)
        a.text('striped end',523,164,17); a.text('union',135,164,17)
    else:
        a.path('M224 332 L393 163 L562 332 Z',BLUE,INK,2)
        for y in range(213,326,20):
            for x in range(250,550,23):
                if abs(x-393) < y-163-12: a.star(x,y,4,PAPER)
        a.arrow('M575 349 C590 313 566 286 547 290',547,290,170)
        a.text('Only blue and stars visible',220,403,20,GREEN)
    return a

def rescue():
    a=Art('Reach. Throw. Row. Go.','Call for help. Swimming rescues are for trained, equipped rescuers only.')
    for x,y,label in [(34,112,'1 / REACH'),(382,112,'2 / THROW'),(34,278,'3 / ROW'),(382,278,'4 / GO: trained only')]:
        a.text(label,x,y,18,GREEN)
        a.path(f'M{x} {y+120} Q{x+32} {y+109} {x+64} {y+120} T{x+128} {y+120} T{x+192} {y+120} T{x+272} {y+120}',stroke=BLUE,width=3)
        a.circle(x+233,y+79,10,'#B98060',INK); a.path(f'M{x+220} {y+108} l12 -17 l13 17',stroke=INK,width=5)
        if label.startswith('1'):
            a.rect(x,y+96,90,13,WOOD); a.circle(x+84,y+75,10,'#E0B797',INK)
            a.line(x+10,y+81,x+67,y+81,GREEN,15); a.line(x+89,y+92,x+221,y+91,WOOD,5)
        elif label.startswith('2'):
            person(a,x+34,y+31,.5)
            a.circle(x+190,y+80,17,RED,INK); a.circle(x+190,y+80,9,PAPER)
            a.path(f'M{x+66} {y+61} Q{x+151} {y+26} {x+190} {y+80}',stroke=TAN,width=2)
        elif label.startswith('3'):
            a.path(f'M{x+40} {y+73} H{x+208} L{x+187} {y+105} H{x+65} Z',TAN,INK,2)
            a.circle(x+103,y+42,8,'#B98060',INK)
            a.rect(x+95,y+54,17,25,RED,INK)
            a.line(x+107,y+80,x+132,y+79,INK,7)
            a.line(x+132,y+79,x+145,y+95,INK,7)
            a.line(x+105,y+62,x+137,y+62,'#B98060',5)
            a.line(x+136,y+55,x+162,y+115,WOOD,4)
            a.line(x+220,y+101,x+208,y+93,INK,3)
        else:
            a.circle(x+142,y+83,22,RED,INK); a.circle(x+142,y+83,12,PAPER)
            a.text('rescue equipment',x+4,y+42,15,RED)
            a.text('qualified rescuer',x+4,y+62,15,RED)
    return a

def camp_layout():
    a=Art('A patrol campsite: plan the separation','Schematic, not to scale. Follow local rules and bear-country requirements.')
    tent(a,97,245,.75); tent(a,180,335,.6)
    a.rect(480,205,129,51,WOOD,INK); a.text('KITCHEN',483,238,17)
    a.rect(507,294,126,92,GREEN,INK)
    a.rect(514,283,112,15,TAN,INK)
    a.text('FOOD',534,348,19,PAPER)
    a.text('food storage',506,414,16)
    a.arrow('M314 232 H443',443,232,0,GREEN)
    a.text('200+ ft in bear country',272,194,16)
    a.arrow('M371 313 H453',453,313,0,BLUE); a.text('wind',377,342,17,BLUE)
    a.text('TENTS',96,376,18)
    return a

def field():
    headers()
    figures={'navigation/compass':compass(),'navigation/red-in-shed':compass(True),'navigation/contours':topo(),'navigation/polaris':polaris(),'navigation/shadow-stick':shadow(),'fire/lays':firelays(),'fire/drown-stir-feel':fireout(),'firstaid/cpr':cpr(),'firstaid/tick':tick(),'firstaid/sling':sling(),'firstaid/splint':splint(),'firstaid/recovery':recovery(),'nature/poison-plants':plants(),'survival/debris-hut':shelter(),'survival/signals':signals(),'water/rescue':rescue(),'camping/layout':camp_layout()}
    for key,a in figures.items(): a.save(key)
    for s in range(1,6): flag(s).save(f'flag/fold-{s}')

if __name__=='__main__':
    knots(); field()
    (ROOT/'art/vector-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print('Wrote',len(manifest),'original vector illustrations.')
