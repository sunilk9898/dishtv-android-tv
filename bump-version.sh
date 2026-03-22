#!/bin/bash
# DishTV Android TV App — Version Bump Script
# Usage: ./bump-version.sh [major|minor|patch]

FILE="version.properties"

MAJOR=$(grep VERSION_MAJOR $FILE | cut -d= -f2)
MINOR=$(grep VERSION_MINOR $FILE | cut -d= -f2)
PATCH=$(grep VERSION_PATCH $FILE | cut -d= -f2)
BUILD=$(grep VERSION_BUILD $FILE | cut -d= -f2)

case "$1" in
  major)
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
    ;;
  minor)
    MINOR=$((MINOR + 1))
    PATCH=0
    ;;
  patch)
    PATCH=$((PATCH + 1))
    ;;
  *)
    echo "Usage: ./bump-version.sh [major|minor|patch]"
    echo "Current: v${MAJOR}.${MINOR}.${PATCH} (build ${BUILD})"
    exit 0
    ;;
esac

BUILD=$((BUILD + 1))

cat > $FILE << EOF
VERSION_MAJOR=$MAJOR
VERSION_MINOR=$MINOR
VERSION_PATCH=$PATCH
VERSION_BUILD=$BUILD
EOF

echo "Version bumped to v${MAJOR}.${MINOR}.${PATCH} (build ${BUILD})"
