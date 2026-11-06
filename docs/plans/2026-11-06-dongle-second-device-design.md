# Dongle: several dongles on one Fritz!Box (#708)

## Problem

The Fritz!Box wizard (`POST /api/fritzbox-setup` → `tr064_provision_sip_client`)
registers every dongle with the fixed SIP username `phoneblock-dongle`
(`config_dongle_username`, since `64765eb9`). `find_client_slot` looks for an
existing client with that username and overwrites it, so re-running the
wizard on the same dongle doesn't create duplicate entries. A *second* dongle on the
same box finds the first one's entry and overwrites it: the first dongle is
silently unregistered (reported in #708, and a nuisance on the test bench).

`64765eb9` dropped MAC-derived names because they were illegible and not
stable (zeroed eFuse in QEMU, NVS wipes). A name field in the wizard would
have to be filled *before* the setup, and the user only learns they needed
it once the first dongle is gone.

## Decision: ask only on conflict

No new input field. The dongle decides by what it knows about its own earlier
setup and asks only when that cannot settle the case.

1. **Own entry → overwrite silently (as today).** The dongle has a stored
   SIP identity from an earlier wizard run (`config_sip_user()` non-empty,
   starting with `phoneblock-`) *and* the stored registrar host equals the
   Fritz!Box host of this run, and the box has a client with that username.
   The stored username is used as is, so dongles provisioned with an old
   MAC-derived name (`phoneblock-5fff54`) keep their entry instead of adding a
   `phoneblock-dongle` beside it.
2. **No PhoneBlock entry on the box → create `phoneblock-dongle`** at the next
   free slot (as today).
3. **Otherwise, if the box has one or more PhoneBlock clients (username
   `phoneblock-dongle` or `phoneblock-dongle-<n>`) that the dongle cannot
   claim as its own → conflict.** Nothing is written. The wizard asks:

   > In der Fritz!Box ist bereits ein PhoneBlock-Dongle eingetragen:
   > PhoneBlock (**629)
   > ( ) Ersetzen – z. B. nach dem Zurücksetzen dieses Dongles
   > ( ) Als weiteres Gerät hinzufügen → „PhoneBlock 2“

   With several existing entries, "Ersetzen" names which one.
   - *Ersetzen* overwrites the chosen entry and adopts its username.
   - *Hinzufügen* takes the lowest free `phoneblock-dongle-<n>` (n ≥ 2) and
     display name `PhoneBlock <n>`, creating a new client at the next free
     slot.
   In both cases the username lands in `sip_user` as today, so later re-runs
   are case 1. An overwritten entry keeps the display name the box shows,
   so a name the user gave it in the Fritz!Box survives a re-run.

The conflict check only reads (`X_AVM-DE_GetClient3`); two-factor
authentication (UPnP error 866) is demanded by the write (`SetClient4`).
So the question comes *before* the "press a button on the box" step, and
the resubmission with the decision runs the existing 2FA flow unchanged.

## Wire format

`POST /api/fritzbox-setup` gains an optional field
`on_conflict=replace:<username>|add`. Without it, a conflict answers

```json
{"ok": false, "conflict": true,
 "entries": [{"username": "phoneblock-dongle", "name": "PhoneBlock", "number": "629"}],
 "add_as": {"username": "phoneblock-dongle-2", "name": "PhoneBlock 2"},
 "message": "<English fallback>"}
```

`name` and `number` are passed through as `X_AVM-DE_GetClient3` reports them
(`NewX_AVM-DE_PhoneName`, `NewX_AVM-DE_InternalNumber` = `620`…`629`) and
omitted when absent; the UI shows the number as `**629`.
`replace:<username>` must name one of the listed PhoneBlock entries; anything
else is refused with a visible message. The 2FA retry carries the decision
along (stashed with the credentials).

## Out of scope

- A free-text device name. The display name can be changed in the Fritz!Box
  UI. Only the username has to be unique, and the wizard picks it.
- Detecting whether an existing entry is currently registered (not exposed
  by `GetClient3`).
