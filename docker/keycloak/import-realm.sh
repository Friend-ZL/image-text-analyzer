#!/bin/bash
# Copy the realm into Keycloak's import directory, then start.
# A named volume on /opt/keycloak/data hides a bind mount placed there on
# first boot, so the file is copied after the volume is mounted.
set -euo pipefail

mkdir -p /opt/keycloak/data/import
cp /opt/keycloak/analyzer-realm.json /opt/keycloak/data/import/analyzer-realm.json

exec /opt/keycloak/bin/kc.sh "$@"
