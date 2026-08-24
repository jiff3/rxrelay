#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME must point to a Java 21 JDK." >&2
  exit 1
fi
exec "$JAVA_HOME/bin/java" -Dmaven.multiModuleProjectDirectory="$project_dir" -classpath "$project_dir/.mvn/wrapper/maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain "$@"
