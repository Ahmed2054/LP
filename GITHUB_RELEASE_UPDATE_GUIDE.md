# GitHub APK Update Flow

## 1. Update app version

Edit `app.json` before each release:

- Increase `expo.version` from `1.0.0` to the next version like `1.0.1`
- Increase `expo.android.versionCode` from `1` to the next number like `2`

## 2. Build the APK

```bash
eas build -p android --profile preview
```

Download the generated APK when the build finishes.

## 3. Upload the APK to GitHub Releases

Create a release tag like `v1.0.1` and upload the APK as a release asset.

Example APK asset URL:

```text
https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPOSITORY/releases/download/v1.0.1/lesson-planner-v1.0.1.apk
```

## 4. Update `version.json`

Edit the root `version.json` file with the new release details:

```json
{
  "version": "1.0.1",
  "versionCode": 2,
  "apkUrl": "https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPOSITORY/releases/download/v1.0.1/lesson-planner-v1.0.1.apk",
  "fileName": "lesson-planner-v1.0.1.apk",
  "notes": "Bug fixes and improvements"
}
```

Commit and push that file to GitHub.

## 5. Point the app at your GitHub `version.json`

In `app.json`, replace:

```json
"updateManifestUrl": "https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/YOUR_REPOSITORY/main/version.json"
```

with your real repository URL.

## 6. Rebuild after changing app config

When you change `app.json`, build a new APK so the installed app contains the latest configuration.

## 7. What users will experience

Inside the app:

- Open `Settings`
- Tap `Check for Update`
- If an update exists, tap `Download & Install`
- Android will open the installer screen
- User confirms the install

Note: Android does not allow silent APK installation for normal apps.
