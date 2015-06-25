# -*- mode: python -*-
a = Analysis(['UpdateChannel'],
             pathex=['d:\\mydoc\\research\\cowifi\\channelhop'],
             hiddenimports=[],
             hookspath=None,
             runtime_hooks=None)
pyz = PYZ(a.pure)
exe = EXE(pyz,
          a.scripts,
          a.binaries,
          a.zipfiles,
          a.datas,
          name='UpdateChannel.exe',
          debug=False,
          strip=None,
          upx=True,
          console=True )
