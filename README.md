# 🔄 Android Git Sync Client

[![Android CI & Build Artifacts](https://github.com/knowlesy/android-git/actions/workflows/android-build.yml/badge.flow.png?branch=main)](https://github.com/knowlesy/android-git/actions/workflows/android-build.yml)
[![CodeQL Security Scan](https://github.com/knowlesy/android-git/actions/workflows/codeql-analysis.yml/badge.flow.png?branch=main)](https://github.com/knowlesy/android-git/actions/workflows/codeql-analysis.yml)

A native, free, secure, and ad-free Android Git sync client built using **Jetpack Compose**, **JGit**, and an Android **Foreground Service** (compliant with Android 14+ `dataSync` specifications). It is designed to run in the background and keep a local directory (e.g., an Obsidian vault) in sync with a remote Git repository.

---

## ✨ Features

- **Reliable Background Syncing:** Runs periodically via a Foreground Service (with a persistent notification and quick-settings tiles) and survives device reboots.
- **Custom Directory Picker:** Bypasses slow Android Scoped Storage / DocumentTree APIs by using `MANAGE_EXTERNAL_STORAGE` to provide fast, direct folder navigation.
- **Automatic Conflict Copies:** Defaults to keeping both local and remote changes. In case of conflicts, remote files are checked out, and conflicting local files are saved as `filename.conflict-YYYYMMDD-HHMMSS.ext` alongside them (preventing data loss and git syntax markers).
- **Smart Clone-and-Overlay:** Preserves pre-existing configuration folders (like `.obsidian-mobile` or `.obsidian` created by opening Obsidian first) when performing a clean clone of the remote repository.
- **D8 Class Shadowing:** Contains an Android runtime patch for the JGit `InflaterCache` bug, preventing decompression crashes on modern Android API levels.

---

## 🔒 Secret Management & Secure Builds

To ensure your private variables (like keystore passwords and credentials) are secure, the workflow builds, signs, and packages your app without committing secrets to the source code:

1. The workflow uses **GitHub Repository Secrets** for key signing.
2. If secrets are not present, the pipeline falls back to building and uploading the standard unsigned debug APK so the build remains clean and passing.
3. Once built, the APK is uploaded directly to the GitHub Actions run summary page as a downloadable artifact.

### How to Configure APK Signing on GitHub:
Go to your repository **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**, and add:
- `SIGNING_KEY` – The base64-encoded string of your release `.jks` keystore file.
  *(Generate this locally using: `openssl base64 -in your_keystore.jks | tr -d '\n'`)*
- `KEY_ALIAS` – The alias of the key in the keystore.
- `KEYSTORE_PASSWORD` – The password for the keystore.
- `KEY_PASSWORD` – The password for the key.

---

## 🛠️ Build Locally
Compile and install the debug APK on an active emulator or USB-connected device:
```bash
./gradlew installDebug
```
