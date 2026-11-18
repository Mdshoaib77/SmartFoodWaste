#!/bin/sh
# Builds the application, compiles the tests and runs them on the separate *_test database (real data is never touched).
# Needs MySQL running and db.properties filled in. Writes TEST_REPORT.md.
sh ./build.sh || exit 1
mkdir -p out-tests
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "out:lib/*" -sourcepath tests -d out-tests tests/com/foodwaste/tests/TestRunner.java \
  || javac -encoding UTF-8 -cp "out:lib/*" -sourcepath tests -d out-tests tests/com/foodwaste/tests/TestRunner.java || exit 1
java -cp "out-tests:out:lib/*" com.foodwaste.tests.TestRunner TEST_REPORT.md
