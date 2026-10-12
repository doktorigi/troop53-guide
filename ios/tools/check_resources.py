"""Standard-library parity check: canonical Android content + authored SVG -> iOS bundle."""
from pathlib import Path
import hashlib, json, re
ROOT=Path(__file__).resolve().parents[2]
def sha(path):
    data=path.read_bytes()
    if path.suffix=='.svg': data=data.replace(b'\r\n', b'\n')
    return hashlib.sha256(data).hexdigest()
manifest=json.loads((ROOT/'ios/art-manifest.json').read_text(encoding='utf8'))
assert len(manifest)==91, 'Expected 89 vector illustrations and two camping scenes'
for record in manifest:
    assert sha(ROOT/record['source'])==record['sha256'], f"Stale iOS export: {record['source']}"
    assert sha(ROOT/record['output'])==record['outputSha256'], f"Changed iOS export: {record['output']}"
    if record['output'].endswith('.pdf'): assert (ROOT/record['output']).read_bytes().startswith(b'%PDF-'), record['output']
folder=ROOT/'ios/Troop53Guide/Resources/Illustrations'
images=0; sections=[]; articles=[]
for file in sorted((ROOT/'app/src/main/assets/guide').glob('*.txt')):
    for line in file.read_text(encoding='utf8').splitlines():
        if line.startswith('=== '): sections.append(line.split('|')[0][4:].strip())
        if line.startswith('# '): articles.append(line.split('|')[0][2:].strip())
        if line.startswith('@ '):
            path=line[2:].split('|')[0].strip()
            extension='.pdf' if path.endswith('.xml') else '.jpg' if path.endswith('.webp') else Path(path).suffix
            assert (folder/Path(path).with_suffix(extension)).is_file(), f'Missing image: {path}'
            images+=1
assert len(sections)==15 and len(sections)==len(set(sections))
assert len(articles)==len(set(articles))
for section in sections: assert (folder/'headers'/f'{section}.pdf').is_file(), section
print(f'Parity passed: {len(sections)} shared sections, {len(articles)} articles, {images} image blocks, {len(manifest)} exported illustrations.')
