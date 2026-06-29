#!/usr/bin/env bash
# Nightly demo reset: wipe accumulated visitor data and re-seed a clean buy->make->sell slice.
#
# `down -v` removes only the erp-demo project's containers, network, and the pgdata volume (project
# scope comes from `name: erp-demo` in compose.demo.yaml) — it never touches other projects sharing
# this host. On the next `up`, the app starts against an empty DB, so DataSeeder re-seeds from scratch
# (it keys off the demo vendor VEND-DEMO, which no longer exists).
#
# Both -f files are required on BOTH commands: dropping the overlay on `up` would rebind the frontend
# to 0.0.0.0 (publicly exposed) instead of loopback. No --build here: a reset implies no code change.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose -f compose.demo.yaml -f compose.oracle.yaml down -v
docker compose -f compose.demo.yaml -f compose.oracle.yaml up -d
