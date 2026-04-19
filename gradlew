#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#
set -e
DIRNAME="$(dirname "$0")"
CLASSPATH="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"
JAVA_OPTS=""
exec java $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
