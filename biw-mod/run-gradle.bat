@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
set "GRADLE=%USERPROFILE%\.gradle-dist\gradle-8.7\bin\gradle.bat"
call "%GRADLE%" %*
