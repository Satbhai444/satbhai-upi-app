@echo off
echo ===========================================
echo Starting Satbhai Pvt Bank Web Server...
echo ===========================================

IF NOT EXIST "sqlite-jdbc.jar" (
    echo Downloading SQLite Database Driver...
    curl.exe -L -o sqlite-jdbc.jar "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.39.2.0/sqlite-jdbc-3.39.2.0.jar"
)

echo Compiling Java Server...
javac -encoding utf8 -cp ".;sqlite-jdbc.jar" *.java

IF %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo Starting Web Server on port 8080...
echo.
echo Open your browser and go to: http://localhost:8080
echo.
java -cp ".;sqlite-jdbc.jar" BankApiServer

pause
