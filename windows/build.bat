@echo off
REM ──────────────────────────────────────────────
REM Radyola Windows — EXE Build Script
REM PyInstaller ile tek dosya EXE oluşturur
REM ──────────────────────────────────────────────

echo [0/3] Python kontrol ediliyor...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo HATA: Python bulunamadi!
    echo Python'u https://www.python.org/downloads/ adresinden indirin.
    echo Kurulum sirasinda "Add Python to PATH" kutusunu isaretleyin.
    pause
    exit /b 1
)

echo [1/3] Gerekli paketler kuruluyor...
python -m pip install pyinstaller pystray Pillow pygame
if %errorlevel% neq 0 (
    echo HATA: Paket kurulumu basarisiz!
    pause
    exit /b 1
)

echo.
echo [2/3] EXE olusturuluyor...
python -m PyInstaller --onefile --windowed --name Radyola --hidden-import=pystray._win32 --hidden-import=PIL --hidden-import=pygame radyola.py
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
