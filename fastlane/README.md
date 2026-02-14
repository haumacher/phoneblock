# Fastlane Metadata

App store metadata for PhoneBlock Mobile, used as single source of truth by:

- **F-Droid** — reads metadata directly from this directory structure
- **GitHub Releases** — the CI workflow extracts changelog from here when creating a release
- **Google Play** — can be uploaded via `fastlane supply` or the `upload-google-play` GitHub Action

## Structure

```
fastlane/metadata/android/en-US/
  title.txt              — App name
  short_description.txt  — Short description (max 80 chars)
  full_description.txt   — Full app description
  changelogs/            — One file per versionCode (e.g. 8.txt for versionCode 8)
  images/                — Screenshots and feature graphic
```

## Changelogs

Changelog files are named by `versionCode` from `pubspec.yaml` (the number after `+`).
For example, version `1.1.2+8` uses `changelogs/8.txt`.

When releasing a new version:
1. Bump version in `phoneblock_mobile/pubspec.yaml`
2. Create `changelogs/<versionCode>.txt`
3. Tag and push — the CI workflow builds, signs, and creates a GitHub release
