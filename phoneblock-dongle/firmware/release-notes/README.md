# PhoneBlock Dongle – Release Notes

One file per released firmware version (`<version>.md`). The dongle's web
UI links the installed version straight to its file, so users can see at
a glance what changed. GitHub lists the directory automatically, so there
is deliberately no hand-maintained index here.

Maintenance:

- Add a new `<version>.md` per release (this is the source of truth,
  reviewed in the pull request). Older files stay untouched.
- `scripts/release.sh` aborts if a clean version (`X.Y.Z`) has no
  `release-notes/<version>.md`.
- Pre-releases (`X.Y.Z-rc1`) are not linkified in the UI and therefore
  need no file of their own.
