@ECHO OFF
@SETLOCAL
FOR %%I IN ("%~dp0.") DO SET "MAVEN_PROJECTBASEDIR=%%~fI"
IF "%JAVA_HOME%"=="" (
  ECHO JAVA_HOME must point to a Java 21 JDK. 1>&2
  EXIT /B 1
)
"%JAVA_HOME%\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
