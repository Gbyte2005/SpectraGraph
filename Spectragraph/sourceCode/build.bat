@echo off
setlocal
set "JAVA_BIN=D:\CODING_AND_PROGRAMMING\IDES_EDITORS_AND_SHELLS\APP-DEV\JAVA\bin"
if not exist "%JAVA_BIN%\javac.exe" set "JAVA_BIN="

echo [SpectraGraph] Cleaning classes...
if exist classes rmdir /s /q classes
mkdir classes

echo [SpectraGraph] Compiling...
if defined JAVA_BIN (
    "%JAVA_BIN%\javac.exe" -J-Xmx512m -encoding UTF-8 -cp "lib/*" -d classes src/main/java/spectragraph/superscript/*.java src/main/java/spectragraph/*.java
) else (
    javac -J-Xmx512m -encoding UTF-8 -cp "lib/*" -d classes src/main/java/spectragraph/superscript/*.java src/main/java/spectragraph/*.java
)
if %ERRORLEVEL% NEQ 0 (
    echo [SpectraGraph] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [SpectraGraph] Extracting libraries...
pushd classes
for %%f in (..\lib\*.jar) do (
    if defined JAVA_BIN (
        "%JAVA_BIN%\jar.exe" -xf "%%f"
    ) else (
        jar -xf "%%f"
    )
)
if exist META-INF rmdir /s /q META-INF
popd

echo [SpectraGraph] Copying assets into classes...
if exist splash.png copy /y splash.png classes\ >nul
if exist icon.png copy /y icon.png classes\ >nul

echo [SpectraGraph] Packaging SpectraGraph.jar...
if exist SpectraGraph.jar del /f /q SpectraGraph.jar
if defined JAVA_BIN (
    "%JAVA_BIN%\jar.exe" --create --file SpectraGraph.jar --manifest manifest.txt -C classes .
) else (
    jar --create --file SpectraGraph.jar --manifest manifest.txt -C classes .
)

echo [SpectraGraph] Updating Standalone distribution...
if exist "Standalone\SpectraGraph\app" (
    copy /y SpectraGraph.jar "Standalone\SpectraGraph\app\SpectraGraph.jar" >nul
    if exist splash.png copy /y splash.png "Standalone\SpectraGraph\app\splash.png" >nul
    if exist icon.ico copy /y icon.ico "Standalone\SpectraGraph\app\icon.ico" >nul
)
if exist "C:\Users\Asus\OneDrive\Desktop\Spectragraph\app" (
    copy /y SpectraGraph.jar "C:\Users\Asus\OneDrive\Desktop\Spectragraph\app\SpectraGraph.jar" >nul
)
if exist "Standalone\splash.png" del /f /q "Standalone\splash.png"

echo [SpectraGraph] Build complete!
