@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Iniciar.ps1" -Modo Cliente
if errorlevel 1 pause
