@echo off
rem Builds the application, compiles the tests and runs them on the separate *_test database (real data is never touched).
rem Needs MySQL running and db.properties filled in. Writes TEST_REPORT.md.
call build.bat
if not exist out-tests mkdir out-tests
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "out;lib\*" -sourcepath tests -d out-tests tests\com\foodwaste\tests\TestRunner.java
if errorlevel 1 goto fail
java -cp "out-tests;out;lib\*" com.foodwaste.tests.TestRunner TEST_REPORT.md
goto end
:fail
echo Test compile failed - trying without --release 8
javac -encoding UTF-8 -cp "out;lib\*" -sourcepath tests -d out-tests tests\com\foodwaste\tests\TestRunner.java
java -cp "out-tests;out;lib\*" com.foodwaste.tests.TestRunner TEST_REPORT.md
:end
