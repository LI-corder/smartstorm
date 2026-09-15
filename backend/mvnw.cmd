@ECHO OFF
REM SmartStorm Maven Wrapper
SETLOCAL EnableExtensions
SET BASE_DIR=%~dp0
SET WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar

IF EXIST "%WRAPPER_JAR%" GOTO run
ECHO wrapper jar not found. Downloading...
IF NOT DEFINED MAVEN_WRAPPER_URL SET MAVEN_WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
powershell -NoProfile -Command "Invoke-WebRequest -Uri '%MAVEN_WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
IF NOT EXIST "%WRAPPER_JAR%" (
    ECHO Failed to download wrapper jar.
    EXIT /B 1
)

:run
REM 优先使用 JDK 17（Spring Boot 3 要求），找不到再回退 JAVA_HOME / PATH
IF EXIST "C:\Program Files\Java\jdk-17\bin\java.exe" (
    SET JAVA_EXE=C:\Program Files\Java\jdk-17\bin\java.exe
) ELSE (
    IF NOT "%JAVA_HOME%"=="" (
        SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
    ) ELSE (
        SET JAVA_EXE=java
    )
)

"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
ENDLOCAL
