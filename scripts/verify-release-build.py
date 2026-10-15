"""Reject accidentally debuggable or unoptimized production artifacts."""
from pathlib import Path
import os,subprocess,xml.etree.ElementTree as E
root=Path(os.environ['ANDROID_HOME'])
aapt=sorted((root/'build-tools').glob('*/aapt'))[-1]
ns='{http://schemas.android.com/apk/res/android}'
for flavor in ['frezzamusic','solasias','thefrezza']:
 apk=Path(f'app/build/outputs/apk/{flavor}/release/app-{flavor}-release-unsigned.apk')
 assert apk.is_file(),str(apk)
 badging=subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True)
 assert 'application-debuggable' not in badging,'Release APK is debuggable'
 mapping=Path(f'app/build/outputs/mapping/{flavor}Release/mapping.txt')
 assert mapping.is_file() and mapping.stat().st_size>100,'Missing R8 mapping'
 assert any('com.frezzamusic.app.data.LiveDriveCatalog -> ' in x and not x.endswith('LiveDriveCatalog:') for x in mapping.read_text().splitlines()),'Catalog class was not obfuscated'
 print(flavor+': production APK, debugging disabled and R8 mapping verified')
