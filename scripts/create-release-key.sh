#!/bin/sh
# Creates MyVault's release signing key.
#
# You type the password; it is stored only in your macOS login Keychain and handed to keytool
# through an environment variable, so it never appears in a file, a command line or shell history.
# Release builds read it from the same Keychain item.
set -eu

KEYSTORE="$HOME/Keys/MyVault/myvault-release.jks"
SERVICE="myvault-release-keystore"
KEYTOOL="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"

if [ -e "$KEYSTORE" ]; then
  echo "A keystore already exists at $KEYSTORE. Refusing to overwrite it."
  exit 1
fi

if security find-generic-password -s "$SERVICE" >/dev/null 2>&1; then
  echo "Using the existing Keychain item \"$SERVICE\"."
else
  echo "Choose a strong password for the MyVault release key (at least 12 characters)."
  echo "Type it, press Return, then type it again. Nothing is shown while you type."
  security add-generic-password -a "$USER" -s "$SERVICE" -l "MyVault release keystore" -w
fi

MYVAULT_KS_PW="$(security find-generic-password -s "$SERVICE" -w)"
export MYVAULT_KS_PW
if [ "${#MYVAULT_KS_PW}" -lt 12 ]; then
  unset MYVAULT_KS_PW
  security delete-generic-password -s "$SERVICE" >/dev/null
  echo "That password is shorter than 12 characters. Nothing was created; run this again."
  exit 1
fi

mkdir -p "$(dirname "$KEYSTORE")"
chmod 700 "$(dirname "$KEYSTORE")"
"$KEYTOOL" -genkeypair \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -alias myvault -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=MyVault Release" \
  -storepass:env MYVAULT_KS_PW -keypass:env MYVAULT_KS_PW
unset MYVAULT_KS_PW
chmod 600 "$KEYSTORE"

echo
echo "Release key created: $KEYSTORE"
echo "Back up this file AND its password somewhere outside this Mac."
