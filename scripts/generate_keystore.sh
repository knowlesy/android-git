#!/bin/bash
set -e

# Prompt securely for the password
echo "========================================="
echo "  Git Sync App Keystore Generator"
echo "========================================="
echo ""
echo "This script will generate a keystore file (release-key.jks) "
echo "and output the base64 string you need for GitHub Secrets."
echo ""

# Read password
read -s -p "Enter a secure password for your new Keystore: " PASS
echo ""
read -s -p "Confirm the password: " PASS_CONFIRM
echo ""

if [ "$PASS" != "$PASS_CONFIRM" ]; then
    echo "Error: Passwords do not match!"
    exit 1
fi

if [ -z "$PASS" ]; then
    echo "Error: Password cannot be empty!"
    exit 1
fi

KEYSTORE_FILE="release-key.jks"
ALIAS="release-alias"

# If keystore already exists, warn user
if [ -f "$KEYSTORE_FILE" ]; then
    echo "Warning: $KEYSTORE_FILE already exists."
    read -p "Do you want to overwrite it? (y/n): " OVERWRITE
    if [ "$OVERWRITE" != "y" ] && [ "$OVERWRITE" != "Y" ]; then
        echo "Aborting."
        exit 0
    fi
    rm "$KEYSTORE_FILE"
fi

echo "Generating keystore..."
keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Git Sync, O=GitSync, C=US" \
  -storepass "$PASS" \
  -keypass "$PASS" \
  >/dev/null 2>&1

echo "Generating Base64-encoded signing key..."
BASE64_KEY=$(openssl base64 -in "$KEYSTORE_FILE" | tr -d '\n')

echo ""
echo "========================================="
echo "               SUCCESS!                  "
echo "========================================="
echo "Keystore file created: $KEYSTORE_FILE"
echo ""
echo "Here are the secret values to add to your GitHub Secrets:"
echo "Go to: https://github.com/knowlesy/android-git/settings/secrets/actions"
echo ""
echo "1. Create a secret named: SIGNING_KEY"
echo "   Value (copy the line below):"
echo "$BASE64_KEY"
echo ""
echo "2. Create a secret named: KEY_ALIAS"
echo "   Value: $ALIAS"
echo ""
echo "3. Create a secret named: KEYSTORE_PASSWORD"
echo "   Value: (The password you just entered)"
echo ""
echo "4. Create a secret named: KEY_PASSWORD"
echo "   Value: (The password you just entered)"
echo "========================================="
