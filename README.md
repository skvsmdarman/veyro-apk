# Veyro APK

Public Android APK distribution for Veyro VPN.

## Latest Release

- **Version Name:** 1.0.0
- **Version Code:** 1
- **Latest Release Asset:** [Veyro-1.0.0.apk](https://github.com/skvsmdarman/veyro-apk/releases/download/v1.0.0/Veyro-1.0.0.apk)
- **Update Manifest:** [update.json](https://raw.githubusercontent.com/skvsmdarman/veyro-apk/main/update.json)

---

## Installation Instructions

1. Download `Veyro-1.0.0.apk` from the [Latest Release](https://github.com/skvsmdarman/veyro-apk/releases/tag/v1.0.0).
2. Open the downloaded file on your Android device.
3. Allow Android Package Installer to install from unknown sources if prompted.
4. Launch Veyro and tap **Connect**.

---

## Release & Update Process

Every future release of Veyro must follow this exact release workflow to ensure seamless auto-updates:

1. **Version Update:** Increment `versionCode` and update `versionName` in `app/build.gradle.kts`.
2. **Build Release APK:** Generate a signed release APK using the same release keystore (`veyro-release-key.jks`).
3. **Calculate Hash & Size:**
   ```powershell
   Get-FileHash -Path Veyro-X.X.X.apk -Algorithm SHA256
   (Get-Item Veyro-X.X.X.apk).Length
   ```
4. **Create GitHub Release:** Tag the release (e.g. `v1.1.0`), create a release title, and attach `Veyro-X.X.X.apk` as a public release asset.
5. **Update Manifest:** Update `update.json` in the `main` branch with the new `versionCode`, `versionName`, `apkUrl`, `size`, and `sha256`.
6. **Push Manifest:** Commit and push `update.json` to the `main` branch.

> [!CAUTION]
> **Key Protection Warning:**
> The same signing key (`veyro-release-key.jks`) MUST be preserved for all future Veyro builds. Never upload or commit the keystore file or keystore passwords to Git.
