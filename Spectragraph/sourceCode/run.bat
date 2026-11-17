@echo off
title SpectraGraph
cd /d "%~dp0"
"D:\CODING_AND_PROGRAMMING\IDES_EDITORS_AND_SHELLS\APP-DEV\JAVA\bin\java.exe" -splash:splash.png --enable-native-access=ALL-UNNAMED -jar SpectraGraph.jar
if %ERRORLEVEL% NEQ 0 pause
