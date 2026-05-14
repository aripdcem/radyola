@echo off
REM ──────────────────────────────────────────────
REM Radyola Windows — EXE Build Script
REM PyInstaller ile tek dosya EXE oluşturur
REM ──────────────────────────────────────────────

echo [1/3] Gerekli paketler kuruluyor...
pip install pyinstaller pystray Pillow pygame
if %errorlevel% neq 0 (
    echo HATA: Paket kurulumu basarisiz! Python ve pip yuklu mu kontrol edin.
    pause
    exit /b 1
)

echo.
echo [2/3] EXE olusturuluyor...
pyinstaller --onefile --windowed --name Radyola --hidden-import=pystray._win32 --hidden-import=PIL --hidden-import=pygame radyola.py
if %errorlevel% neq 0 (
    echo HATA: EXE olusturulamadi! Hata mesajlarini kontrol edin.
    pause
    exit /b 1
)

echo.
echo [3/3] Tamamlandi!
echo.
echo EXE dosyasi: dist\Radyola.exe
echo.
pause
