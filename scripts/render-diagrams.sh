#!/usr/bin/env bash
# Renders the Report 4 diagrams (docs/diagrams/src/*.puml) to PNG in docs/diagrams/png.
#
#   python3 scripts/report4_diagrams.py              # write the .puml sources first
#   PLANTUML_JAR=/path/to/plantuml.jar scripts/render-diagrams.sh
#
# Uses, in order: $PLANTUML_JAR with Java, a `plantuml` command on the PATH, or the plantuml/plantuml
# Docker image. Without Graphviz (`dot`) it uses PlantUML's built-in layout engine (Smetana).
set -euo pipefail
cd "$(dirname "$0")/.."

SRC=docs/diagrams/src
OUT=docs/diagrams/png
mkdir -p "$OUT"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# Copy the sources; without Graphviz, ask each diagram for the built-in layout.
for f in "$SRC"/*.puml; do
    if command -v dot >/dev/null 2>&1; then
        cp "$f" "$WORK/"
    else
        sed '0,/^@startuml/s//@startuml\n!pragma layout smetana/' "$f" > "$WORK/$(basename "$f")"
    fi
done

JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
if [ -n "${PLANTUML_JAR:-}" ]; then
    "$JAVA" -Djava.awt.headless=true -DPLANTUML_LIMIT_SIZE=16384 -jar "$PLANTUML_JAR" -tpng -o "$PWD/$OUT" "$WORK"/*.puml
elif command -v plantuml >/dev/null 2>&1; then
    PLANTUML_LIMIT_SIZE=16384 plantuml -tpng -o "$PWD/$OUT" "$WORK"/*.puml
elif command -v docker >/dev/null 2>&1; then
    docker run --rm -u "$(id -u):$(id -g)" -e PLANTUML_LIMIT_SIZE=16384 \
        -v "$WORK":/src -v "$PWD/$OUT":/out plantuml/plantuml -tpng -o /out /src/*.puml
else
    echo "No PlantUML found: set PLANTUML_JAR, install plantuml, or start Docker." >&2
    exit 1
fi
echo "Rendered $(ls "$OUT"/*.png | wc -l) diagrams into $OUT"
