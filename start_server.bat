@echo off
title PowerTrack Server
echo ========================================================
echo  PowerTrack Backend ^& Live Tracking Dashboard
echo ========================================================
echo.
echo Starting PowerTrack server on http://localhost:5000 ...
cd /d "%~dp0backend"
node src/server.js
pause
