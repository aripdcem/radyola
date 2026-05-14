@echo off
REM ──────────────────────────────────────────────
REM Radyola Windows — EXE Build Script
REM PyInstaller ile tek dosya EXE oluşturur
REM ──────────────────────────────────────────────

echo [1/3] Gerekli paketler kontrol ediliyor...
pip install pyinstaller pystray Pillow pygame >nul 2>&1

echo [2/3] EXE olusturuluyor...
pyinstaller ^
    --onefile ^
    --windowed ^
    --name Radyola ^
    --icon=NONE ^
    --add-data "." ^
    --hidden-import=pystray._win32 ^
    --hidden-import=PIL ^
    --hidden-import=pygame ^
    radyola.py

echo [3/3] Tamamlandi!
echo.
echo EXE dosyasi: dist\Radyola.exe
echo.
pause
