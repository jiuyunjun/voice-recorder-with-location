#!/bin/sh

##############################################################################
##
##  Gradle startup script for POSIX generated for this project.
##
##############################################################################

APP_HOME=$(cd "$(dirname "$0")" >/dev/null 2>&1 && pwd -P)
APP_BASE_NAME=${0##*/}
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE=java
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no java command could be found in PATH." >&2
    exit 1
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

eval "set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \"-Dorg.gradle.appname=$APP_BASE_NAME\" -classpath \"$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"\$@\""
exec "$JAVA_EXE" "$@"

