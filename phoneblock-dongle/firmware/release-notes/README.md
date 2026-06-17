# PhoneBlock Dongle – Release Notes

One file per released firmware version (`<version>.md`). The dongle's web
UI links the installed version straight to its file, so users can see at
a glance what changed. GitHub lists the directory automatically, so there
is deliberately no hand-maintained index here.

Maintenance:

- Add a new `<version>.md` per release (this is the source of truth,
  reviewed in the pull request). Older files stay untouched.
- `scripts/release.sh` aborts if a version has no notes file. The check
  uses the suffix-stripped base, so it covers both `X.Y.Z` and
  `X.Y.Z-rc1`.
- A pre-release (`X.Y.Z-rc1`) is a preview of the upcoming `X.Y.Z`: the
  UI links it to `X.Y.Z.md`, so that file must already exist when the rc
  is cut. There is no separate notes file per rc.
