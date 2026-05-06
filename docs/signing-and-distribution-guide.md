# Signing and Distribution Guide

This guide defines one safe strategy so updates always install correctly.

## One-key strategy (required)

Use one release keystore identity for all three channels:
- CI release artifacts
- Firebase App Distribution artifacts
- updater-served APKs (GitHub Releases)

If any channel uses a different certificate, Android update install fails.

## Step 1: Create release keystore once

Run locally (example):

```powershell
keytool -genkeypair -v `
  -keystore release.jks `
  -alias amanos_release `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

Store safely:
- keystore file (`release.jks`)
- keystore password
- key alias
- key password

## Step 2: Add GitHub repository secrets

Create these secrets in GitHub repo settings:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Generate base64 for `ANDROID_KEYSTORE_BASE64`:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

## Step 3: Understand build behavior

Application modules read signing values from env/property keys:
- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Release workflow now:
1. validates signing secrets
2. decodes keystore to `.secure/release.jks`
3. builds signed `release` APKs
4. publishes them to GitHub Releases

## Step 4: Firebase App Distribution (optional)

Use the same signed release APK outputs from CI.

Recommended:
- upload `contacts-release.apk`
- upload `messaging-release.apk`
- upload `agenttest-release.apk`

Keep Firebase builds signed by the same key used in GitHub release pipeline.

## Step 5: Updater compatibility

Updater works when:
- package name unchanged
- update APK signed with same certificate
- user allows installer flow

So, signed GitHub release APKs + same key = updater-compatible updates.

## Rotation note

Do not rotate signing key casually. Rotating key without migration means existing installs cannot update.

## Recovery checklist

Back up in two safe places:
- `release.jks`
- keystore password
- alias
- key password

