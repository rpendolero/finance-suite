#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/publish-docker.sh VERSION DOCKERHUB_USER [all|server|frontend]

Examples:
  scripts/publish-docker.sh 0.5.3 myuser
  scripts/publish-docker.sh 0.5.3 myuser frontend

Uses the checked-out source version. Builds all selected images before pushing.
Docker login prompts for credentials; credentials are never passed as arguments.
EOF
}

fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

if [[ "${1:-}" == '--help' || "${1:-}" == '-h' ]]; then
  usage
  exit 0
fi
if (( $# < 2 || $# > 3 )); then
  usage >&2
  exit 1
fi

version="$1"
docker_user="$2"
selection="${3:-all}"
[[ "$version" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || fail 'Invalid image version.'
[[ "$docker_user" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail 'Invalid Docker Hub username.'

case "$selection" in
  all) images=(finance-server finance-dashboard) ;;
  server) images=(finance-server) ;;
  frontend) images=(finance-dashboard) ;;
  *) fail 'Select all, server or frontend.' ;;
esac

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

source_version="$(awk '
  /<artifactId>finance-suite<\/artifactId>/ { suite = 1 }
  suite && /<version>/ {
    sub(/.*<version>/, ""); sub(/<\/version>.*/, ""); print; exit
  }
' pom.xml)"
[[ "$version" == "$source_version" ]] || fail "Source version is $source_version; use the source for release $version."

for image in "${images[@]}"; do
  [[ -f "$image/Dockerfile" ]] || fail "Missing $image/Dockerfile."
  if [[ "$image" == 'finance-dashboard' ]]; then
    dashboard_version="$(sed -n 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$image/package.json")"
    [[ "$version" == "$dashboard_version" ]] || fail "Dashboard version is $dashboard_version, expected $version."
  fi
done

command -v docker >/dev/null 2>&1 || fail 'Docker is not installed.'
docker info >/dev/null || fail 'Docker daemon is unavailable.'
docker login --username "$docker_user"

for image in "${images[@]}"; do
  context="$project_dir"
  [[ "$image" != 'finance-dashboard' ]] || context="$project_dir/finance-dashboard"
  printf 'Building %s/%s:%s\n' "$docker_user" "$image" "$version"
  docker build --file "$project_dir/$image/Dockerfile" \
    --tag "$docker_user/$image:$version" "$context"
done

for image in "${images[@]}"; do
  printf 'Publishing %s/%s:%s\n' "$docker_user" "$image" "$version"
  docker push "$docker_user/$image:$version"
done

printf 'Published version %s successfully.\n' "$version"
