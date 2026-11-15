# Security & Signing Remediation

## 1. Removal of Tracked Keystore (`app/release.keystore`)

A release signing keystore (`app/release.keystore`) was previously committed to the repository. Even when protected by a passphrase, storing any signing keystore in Git (especially in a public repository) exposes it to offline brute-force attacks and allows anyone with the signing key to publish spoofed updates under the same application ID (`com.example.on_device_qwen`).

Remediation already applied in this repository:
- Removed `app/release.keystore` from the tracked tree (`git rm app/release.keystore`).
- Expanded `.gitignore` and `app/.gitignore` to ignore `*.keystore`, `*.jks`, `*.p12`, `*.pk8`, `*.pem`, `keystore.properties`, and `*.bak`.
- Enforced fail-closed release signing in `app/build.gradle.kts` (`preReleaseBuild` / `packageRelease` fail unless all `ANDROID_KEYSTORE_*` environment variables are provided, or `-PallowUnsignedRelease=true` is explicitly passed for CI verification).
- Added a tracked-keystore guard in `.github/workflows/android-ci.yml` and `.github/workflows/release.yml`.

## 2. Rewriting Git History (`git filter-repo`)

Deleting the keystore in a new commit leaves the binary file accessible in earlier Git commits. The repository owner should purge it from history and rotate to a fresh release key:

1. **Take a fresh mirror backup first** (and coordinate with collaborators because history SHAs will change):
   ```bash
   git clone --mirror https://github.com/<owner>/<repo>.git repo-backup.git
   ```
2. **Strip `app/release.keystore` from all commits and tags** using [`git-filter-repo`](https://github.com/newren/git-filter-repo):
   ```bash
   git clone --mirror https://github.com/<owner>/<repo>.git repo-clean.git
   cd repo-clean.git
   git filter-repo --invert-paths --path app/release.keystore
   ```
3. **Verify the file is absent from all history**, then force-push all branches and tags:
   ```bash
   git log --all --stat -- app/release.keystore
   git push --force --all
   git push --force --tags
   ```
4. Re-clone fresh working copies rather than pulling over pre-rewrite branches. Because forks, caches, or clones created before the purge may still contain the old keystore, **treat the old signing key as permanently compromised and generate a new release keystore**.

## 3. GitHub Actions Release Secrets Setup

Generate a fresh upload/release keystore outside any repository directory (never inside the workspace):

```bash
keytool -genkeypair -v \
  -keystore on-device-llm-release.jks \
  -alias on_device_llm \
  -keyalg RSA -keysize 4096 -validity 10000
```

Encode the keystore as single-line Base64 (without line wraps):
- **Linux / macOS**: `base64 -w 0 on-device-llm-release.jks` (macOS: `base64 -i on-device-llm-release.jks | tr -d '\n'`)
- **PowerShell**: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("on-device-llm-release.jks"))`

Add these four **Repository Secrets** under **Settings -> Secrets and variables -> Actions** (used by `.github/workflows/release.yml`):
- `ANDROID_KEYSTORE_BASE64`: single-line Base64 contents of the `.jks` / `.keystore` file
- `ANDROID_KEYSTORE_PASSWORD`: keystore store password
- `ANDROID_KEY_ALIAS`: key alias (e.g., `on_device_llm`)
- `ANDROID_KEY_PASSWORD`: private key password

Store an offline encrypted backup of the `.jks` file and passphrases in a password manager.

## 4. Key Rotation (APK Signature Scheme v3) vs. Fresh-Key Reinstall

Android rejects in-place APK upgrades whose signing certificate differs from the installed package unless a valid **APK Signature Scheme v3 key-rotation lineage** links the old key to the new key.

### Option A: APK Signature Scheme v3 Key Rotation (in-place upgrade)
- Only possible if you still possess the old keystore **and** distribute an intermediate/rotated APK signed with **both** the old and new certificates via `apksigner sign --rotation-min-sdk-version 28` using a `signing_lineage` generated with `apksigner rotate`.
- Limitations:
  1. Devices on Android 8.x (`minSdk = 26`, API 26–27) do not support v3 signing scheme rotation.
  2. Any attacker who already obtained the leaked old keystore can still sign APKs trusted by existing installs until users install the rotated APK and the old lineage is untrusted; even then, v3 only authorizes upgrades from the v3-capable lineage, and prior compromises cannot be retroactively revoked on offline APKs.

### Option B: Fresh-Key Reinstall (Recommended After Compromise)
Because the legacy keystore was publicly committed, rotating cleanly to an unlinked fresh keystore is the strongest security posture. Inform existing users in release notes that upgrading from versions `<= 1.3.0` requires uninstalling the old APK before installing the new release, and document how to preserve their data beforehand:

1. **Preserve conversations**: Export chats (JSON/Markdown) from within the app to device storage (`Downloads/` or user-selected SAF folder) **before** uninstalling. (Note: `android:allowBackup="false"` and backup exclusion rules intentionally block cloud/ADB backup of `qwen_chat.db` and preferences.)
2. **Preserve local models**:
   - Models stored in shared user directories (`Downloads/`, SD card, or custom SAF directories) remain untouched when the app is uninstalled and can be re-imported afterward.
   - Any model previously copied exclusively into app-private internal storage (`/data/data/com.example.on_device_qwen/files/models/`) will be deleted on uninstall by Android—re-import from your original `.litertlm` / `.gguf` source file after installing the freshly signed APK.
3. **Uninstall the old build -> install the new release APK -> re-import exported chats / models**.

## 5. Offline & Storage Privacy Posture

- `INTERNET` and `MANAGE_EXTERNAL_STORAGE` permissions are removed from `AndroidManifest.xml`; inference, chat history, and model management are strictly offline.
- `android:allowBackup="false"` plus explicit exclusions in `backup_rules.xml` and `data_extraction_rules.xml` prevent `qwen_chat.db`, `on_device_qwen_prefs.xml`, `files/models`, and caches from leaving the device via cloud backup or device-to-device transfer.
