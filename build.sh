#!/usr/bin/env bash
# Simple build script for ParanoiaPlus (Maven)
set -e
echo "Building ParanoiaPlus with Maven..."
mvn clean package -DskipTests
echo "Build finished. JAR will be in target/ directory."
