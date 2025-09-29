@REM
@REM Copyright 2015 the original author or authors.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM      https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@IF EXIST "%~dp0\gradlew.bat" GOTO init

@REM Determine the Java command to use to start the JVM.
@IF DEFINED JAVA_HOME GOTO findJavaFromJavaHome

@SET JAVACMD=java
@GOTO run

:findJavaFromJavaHome
@SET JAVACMD="%JAVA_HOME%\bin\java"

:run
@REM Determine the script path.
@SET SCRIPT_DIR=%~dp0

@REM Determine the Gradle wrapper JAR file.
@SET GRADLE_WRAPPER_JAR="%SCRIPT_DIR%\gradle\wrapper\gradle-wrapper.jar"

@IF NOT EXIST %GRADLE_WRAPPER_JAR% GOTO noWrapperJar

@REM Start Gradle.
%JAVACMD% %JVM_OPTS% -jar %GRADLE_WRAPPER_JAR% %*
@GOTO end

:noWrapperJar
@ECHO Gradle wrapper JAR not found. Attempting to use a default Gradle version.
@REM You might need to install Gradle manually or configure a default version here.
@REM For now, we'll just exit.
@EXIT /B 1

:end
