@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0java-spring-v2\Start-Valenbot.ps1"
pause
