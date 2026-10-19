@echo off
rem Project-local verified Gradle bootstrap. See tooling\GradleWrapperMain.java.
set "APP_HOME=%~dp0"
java -cp "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
