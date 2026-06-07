# 🔄 Android Git Sync Client

[![Android CI & Build Artifacts](https://github.com/knowlesy/android-git/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/knowlesy/android-git/actions/workflows/android-build.yml)
[![CodeQL Security Scan](https://github.com/knowlesy/android-git/actions/workflows/codeql-analysis.yml/badge.svg?branch=main)](https://github.com/knowlesy/android-git/actions/workflows/codeql-analysis.yml)

A native, free, secure, and ad-free Android Git synchronization client built using **Jetpack Compose**, **JGit**, and an Android **Foreground Service** (compliant with Android 14+ `dataSync` specifications). 

This app runs locally on your device to keep any local folder (such as an **Obsidian vault**) synchronized with a remote Git repository securely, reliably, and in the background—without ads, trackers, or telemetry.

---

## 🛠️ Architecture & Under-the-Hood Fixes

Standard JGit implementations fail on modern Android due to storage bottlenecks and OS resource-management differences. This app implements custom workarounds:

* **Direct Filesystem Access (`MANAGE_EXTERNAL_STORAGE`):** Rather than using the slow and restrictive Android Storage Access Framework (SAF) DocumentTree APIs, the app leverages direct filesystem APIs. This allows JGit to run operations at full filesystem speed, making clones and pulls of large vaults instant.
* **Smart Clone-and-Overlay:** If you open Obsidian first, it automatically creates config files like `.obsidian-mobile`. A fresh Git clone would normally fail or require forcing disjoint history merges. The sync engine automatically caches these configuration folders in memory, runs a clean remote clone, and overlays the configurations back—preventing conflicts.
* **JGit Inflater Cache Patch (D8 Shadowing):** Android's `InflaterInputStream` automatically calls `end()` on the shared `Inflater` instance when a stream is closed. JGit caches these instances, causing decompression crashes (`Inflater has been closed`) on subsequent sync cycles. The build pipeline shadows `org.eclipse.jgit.lib.InflaterCache` at compile-time to replace cached decompressors with a custom `SafeInflater` wrapper that safely ignores premature stream-termination calls.
* **Android Media Scanner Integration:** Changes downloaded by Git are immediately registered with the Android Media Store. This ensures newly synchronized notes, files, or attachments immediately show up in Obsidian, the system Files app, and image galleries.

---

## ⚙️ In-Depth Configuration Guide

The app's **Settings** dashboard allows you to customize your sync behavior:

| Setting Field | Description | Best Practice / Format |
| :--- | :--- | :--- |
| **Git URL** | The HTTPS URL of your remote Git repository. | `https://github.com/username/repository.git` |
| **Username** | The name attached to your Git commits. | `John Doe` |
| **Email** | The email address associated with your Git commits. | `john.doe@example.com` |
| **PAT Token** | Personal Access Token used for HTTPS authentication. | `ghp_xxxxxxxxxxxxxxxxxxxxxx` *(Do not use your GitHub account password)* |
| **Sync Folder** | The target directory on your phone to be synchronized. | Select path starting from `/storage/emulated/0/...` (e.g. `/storage/emulated/0/Documents/ObsidianVault`) |
| **Sync Interval** | The frequency at which background sync cycles occur. | Selectable: `1`, `5`, `10`, `30`, `60`, `90`, `180`, or `300` minutes. |
| **Conflict Strategy** | How the sync engine resolves concurrent local and remote edits. | Choose: `Conflict Copy`, `Keep Local`, or `Keep Remote` (see below). |

---

## 🛡️ Conflict Resolution Strategies

When a file is modified both locally (on your phone) and remotely (e.g., on your computer) before a sync occurs, a conflict is detected. The app resolves these conflicts automatically:

1. **Conflict Copy (Default & Recommended):**
   * Pre-existing local modifications remain untouched at the original filename (e.g. `My Note.md`).
   * The conflicting remote modifications are downloaded and written to a separate file alongside it, named with a timestamp: `My Note.conflict-YYYYMMDD-HHMMSS.md`.
   * This ensures **zero data loss** and keeps your original notes free from standard Git conflict markers (`<<<<<<< HEAD`).
2. **Keep Local (Local Wins):**
   * Discards the conflicting remote changes and commits/pushes your local version.
3. **Keep Remote (Remote Wins):**
   * Discards your unsynced local changes and overwrites the local file with the remote version.

---

## 🚀 Quick Start: Syncing an Obsidian Vault

Follow these steps to set up free, secure Git syncing for your Obsidian notes:

### Step 1: Create a GitHub Repository
1. Log in to GitHub and create a **Private** repository (e.g., `my-obsidian-vault`).
2. Do not initialize it with a README or `.gitignore` (keep it completely empty).

### Step 2: Generate a Personal Access Token (PAT)
1. Go to your GitHub profile: **Settings** -> **Developer Settings** -> **Personal Access Tokens** -> **Tokens (classic)**.
2. Click **Generate new token (classic)**.
3. Set the name to `Android Git Sync` and tick the **`repo`** scope box.
4. Copy the generated token (`ghp_...`) and save it somewhere secure.

### Step 3: Create Vault in Obsidian
1. Open Obsidian on your Android device.
2. Create a new vault and note down the path (e.g. `Documents/MyVault`). This creates the vault folder and the `.obsidian-mobile` settings folder.

### Step 4: Configure the Git Sync App
1. Install and open the **Git Sync** app.
2. Grant the **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) and **Notifications** permissions when prompted.
3. In the **Settings** tab:
   * **Git URL:** Paste your private GitHub repo URL.
   * **Username & Email:** Enter your name and email.
   * **PAT Token:** Paste your `ghp_...` token.
   * **Sync Folder:** Tap **Select Folder** and navigate to your vault folder under `Documents/MyVault`.
   * **Sync Interval:** Set to your preferred background frequency (e.g., `10 minutes`).
   * **Conflict Strategy:** Leave as `Conflict Copy`.
4. Turn on the **Enable Auto-Sync** toggle.
5. Tap **Sync Now** to run the initial sync. The app will automatically clone your remote repository, preserve your local `.obsidian-mobile` configurations, and push your initial notes up to GitHub.

---

## 🔒 Security & Safe CI/CD Builds

This repository is configured to build and package your app safely using **GitHub Actions**. Your private signing keys and credentials are never checked into source control:

1. **Repository Secrets:** Keystore secrets are managed via GitHub Actions Repository Secrets.
2. **Fallback compilation:** If secrets are not configured, the workflow compiles and uploads a standard unsigned debug APK so the build remains passing.
3. **Downloadable Artifacts:** Every successful build uploads the compiled APK directly to the Actions run summary page as a zip file.

### How to configure APK signing in your GitHub Fork:
1. Generate a signing key locally using your terminal:
   ```bash
   keytool -genkeypair -v -keystore release-key.jks -alias release-alias -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Git Sync, O=GitSync, C=US"
   ```
2. Convert the generated key to a base64 string:
   ```bash
   openssl base64 -in release-key.jks | tr -d '\n'
   ```
3. In your GitHub repository, go to **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret** and add:
   * `SIGNING_KEY` – Paste the base64 string from step 2.
   * `KEY_ALIAS` – Enter `release-alias`.
   * `KEYSTORE_PASSWORD` – The password you entered in step 1.
   * `KEY_PASSWORD` – The password you entered in step 1.

---

## 🛠️ Build Locally

To build and install the debug APK on a connected emulator or physical Android device via USB:
```bash
./gradlew installDebug
```
