#!/bin/bash
# Prepara las sesiones de Claude Code en la web: instala la base PostgreSQL
# aislada (PGlite) que usan las pruebas. No usa credenciales ni toca Supabase real.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"
# install (no ci): aprovecha la caché del contenedor; --ignore-scripts como en el README.
npm install --ignore-scripts --no-audit --no-fund
