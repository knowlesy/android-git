# Privacy Policy for Git Sync Android Client

This Privacy Policy describes how your data is handled by the **Git Sync** Android application.

## 1. Zero Data Collection
We believe in absolute privacy. 
* **No Analytics:** The app does not collect any usage data, logs, or crash reports.
* **No Ads/Trackers:** The app contains zero advertisements, trackers, or marketing SDKs.
* **No Telemetry:** We do not send any telemetry or analytics back to ourselves or any third party.

## 2. Local Storage & Encryption
* All configuration details (like repository URLs, usernames, emails, and Personal Access Tokens) are stored locally on your device.
* Sensitive values like Personal Access Tokens (PATs) are hardware-encrypted using the Android Keystore system (`EncryptedSharedPreferences`) and are not readable by other applications.
* The synchronized Git files are checked out directly to the folder you specify on your local storage.

## 3. Network Communication
* The app only communicates with the **Git remote server** (e.g., GitHub, GitLab, or your own self-hosted Git server) that you explicitly configure in the Settings screen.
* Network traffic is encrypted using standard HTTPS protocol during fetch, pull, and push synchronization cycles.
* We do not operate any intermediary servers or proxy servers. All communication is direct between your device and your chosen Git host.

## 4. Children's Privacy
Because we collect absolutely no data, this app is fully compliant with COPPA (Children's Online Privacy Protection Act) and GDPR regulations.

## 5. Contact
If you have any questions or feedback regarding this policy, please reach out to the project maintainer via the contact info on the [GitHub Profile of knowlesy](https://github.com/knowlesy).
