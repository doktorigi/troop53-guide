"""Read-only UI checks plus reversible taps on an already-installed test build."""
import subprocess, re, time, sys, os, shutil
from pathlib import Path
import xml.etree.ElementTree as ET
SDK=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',Path.home()/'AppData/Local/Android/Sdk')))
ADB=os.environ.get('ADB') or shutil.which('adb') or str(SDK/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb'))
SERIAL=os.environ.get('ANDROID_SERIAL','')
ROOT=Path(__file__).resolve().parents[1]
def adb(*args): return subprocess.check_output([ADB,*(['-s',SERIAL] if SERIAL else []),*args])
def hierarchy():
    adb('shell','uiautomator','dump','/sdcard/troop53-window.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/troop53-window.xml'))
def tap_node(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(.25)
def tap_text(text):
    tree=hierarchy()
    node=next(n for n in tree.iter('node') if n.attrib.get('text')==text)
    tap_node(node)
def screenshot(name):
    (ROOT/'art/preview'/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
if __name__=='__main__':
    adb('shell','am','force-stop','com.doktorigi.troop53')
    adb('shell','am','start','-n','com.doktorigi.troop53/.MainActivity')
    tree=hierarchy(); edit=next(n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText')
    if len(sys.argv)>1 and sys.argv[1]=='camping':
        tap_node(edit); adb('shell','input','text','Pitching'); adb('shell','input','keyevent','4')
        tap_text('Pitching a Tent')
        for _ in range(8):
            images=[n for n in hierarchy().iter('node') if n.attrib.get('content-desc','').startswith('A separate rainfly')]
            if images and images[0].attrib['bounds']!='[0,0][0,0]': break
            adb('shell','input','swipe','600','2250','600','620','400')
        assert images, 'Missing camping image'
        screenshot('tent-article'); tap_node(images[0]); screenshot('tent-viewer')
        adb('shell','input','keyevent','4')
        print('Camping WebP card and full-screen viewer opened.'); sys.exit(0)
    tap_node(edit); adb('shell','input','text','bowline'); adb('shell','input','keyevent','4')
    tap_text('Bowline'); screenshot('bowline-article')
    tap_text('Knot Practice  ›'); screenshot('bowline-practice')
    tap_text('Next'); screenshot('bowline-practice-next')
    tap_text('Close practice')
    tree=hierarchy(); image=next(n for n in tree.iter('node') if n.attrib.get('content-desc','').startswith('Step 1:'))
    tap_node(image); screenshot('bowline-viewer')
    tap_text('+'); screenshot('bowline-viewer-zoom')
    adb('shell','input','keyevent','4'); screenshot('bowline-back')
    assert any(n.attrib.get('text')=='Knot Practice  ›' for n in hierarchy().iter('node'))
    print('Article, Practice Next, image viewer, zoom button, and system Back passed.')
