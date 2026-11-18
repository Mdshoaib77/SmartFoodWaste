#!/bin/sh
# Compiles the application into out/ (Java 8 compatible).
mkdir -p out
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "lib/*" -sourcepath src -d out src/com/foodwaste/Main.java src/com/foodwaste/gui/GuiMain.java \
  || javac -encoding UTF-8 -cp "lib/*" -sourcepath src -d out src/com/foodwaste/Main.java src/com/foodwaste/gui/GuiMain.java \
  && echo "Build OK -> out/"
