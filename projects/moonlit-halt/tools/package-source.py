"""Package the runnable v2 study, original v1, assets, renders and checks."""
from pathlib import Path
import zipfile
root=Path(__file__).resolve().parent.parent
archive=root/'dist/moonlit-halt-source.zip'
files=[root/name for name in ['README.md','TEST-REPORT.md','ART-BRIEF.md','IMPORT-RECORD.md','SOURCE-MANIFEST.json'] if (root/name).is_file()]
for folder in ['dist','tests','tools']:
 files += [p for p in (root/folder).rglob('*') if p.is_file() and p!=archive and '__pycache__' not in p.parts]
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(set(files)):z.write(p,'moonlit-halt/'+str(p.relative_to(root)))
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 assert 'moonlit-halt/dist/v1/scene.js' in z.namelist()
 assert 'moonlit-halt/dist/assets/moon.jpg' in z.namelist()
 print('Source ZIP verified:',len(z.namelist()),'files,',archive.stat().st_size,'bytes')
