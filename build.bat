@echo off
rem Compiles the application into out\ (Java 8 compatible). Works when the folder path contains spaces.
if not exist out mkdir out
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "lib\*" -sourcepath src -d out src\com\foodwaste\Main.java src\com\foodwaste\gui\GuiMain.java
if errorlevel 1 goto fallback
echo Build OK
goto end
:fallback
echo (--release 8 not accepted by this JDK, compiling with the default target)
javac -encoding UTF-8 -cp "lib\*" -sourcepath src -d out src\com\foodwaste\Main.java src\com\foodwaste\gui\GuiMain.java
if errorlevel 1 (echo BUILD FAILED) else (echo Build OK)
:end
