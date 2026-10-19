#!/usr/bin/env bash
# Builds the release APK and publishes it on the site (TPSArena_Site/releases/android/).
#
#   tools/build_release.sh            build + copy to ../TPSArena_Site/releases/android/
#   tools/build_release.sh --no-copy  build only
#
# Before building, refreshes the bundled mkcert CA (res/raw/tps_arena_ca.crt) from the site's
# certs/tps-arena-ca.crt, so the APK trusts the HTTPS certificate of the machine serving the
# site. Only the public CA certificate is copied, never a key. Signed with keystore.properties
# when it exists, else with the debug key.
set -euo pipefail
cd "$(dirname "$0")/.."

site=../TPSArena_Site
ca_src=$site/certs/tps-arena-ca.crt
ca_dst=app/src/main/res/raw/tps_arena_ca.crt

if [ -f "$ca_src" ]; then
	if ! grep -q "BEGIN CERTIFICATE" "$ca_src" || grep -q "PRIVATE KEY" "$ca_src"; then
		echo "$ca_src n'est pas un certificat public : abandon." >&2
		exit 1
	fi
	cp "$ca_src" "$ca_dst"
	echo "CA du site copiée dans $ca_dst"
else
	echo "Pas de $ca_src : la CA déjà présente dans l'app est gardée."
fi

./gradlew --console=plain assembleRelease
apk=app/build/outputs/apk/release/app-release.apk

if [ "${1:-}" != "--no-copy" ]; then
	mkdir -p "$site/releases/android"
	cp "$apk" "$site/releases/android/TPSArenaCompanion.apk"
	echo "APK publié : $site/releases/android/TPSArenaCompanion.apk (le site le voit en 30 s)"
else
	echo "APK : $apk"
fi
