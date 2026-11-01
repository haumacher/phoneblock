# PhoneBlock spam-call blocker for the Fritz!Box

This image runs the [PhoneBlock Dongle](https://phoneblock.net/phoneblock/dongle)
firmware as a Linux service. It registers as an IP phone with your Fritz!Box and
answers a call only if the caller's number is listed in the community-maintained
[PhoneBlock spam database](https://phoneblock.net/phoneblock/). Spam callers hear
an announcement; all other calls ring as usual.

It has the same web UI, settings and version as the ESP32 dongle, and contains
exactly the Debian package from the
[PhoneBlock APT repository](https://haumacher.github.io/phoneblock/apt/).

## Tags

| Tag | Content |
| --- | --- |
| `latest` | the newest stable release |
| `beta` | the newest release, including pre-releases |
| `1.7.0`, `1.7.0-beta7`, … | a specific firmware version |

Platforms: `linux/amd64`, `linux/arm64`, `linux/arm/v7` (e.g. Raspberry Pi, most NAS).

## Quick start

```sh
docker run -d --name phoneblock --network host --restart unless-stopped \
  -v phoneblock:/var/lib/phoneblock -e TZ=Europe/Berlin \
  phoneblock/answerbot2:latest
```

Or with Docker Compose:

```yaml
services:
  phoneblock:
    image: phoneblock/answerbot2:latest
    network_mode: host
    restart: unless-stopped
    environment:
      TZ: Europe/Berlin
    volumes:
      - phoneblock:/var/lib/phoneblock
volumes:
  phoneblock:
```

Then open `http://<docker-host>:8080/` in your browser and follow the setup:

1. **Fritz!Box:** enter your Fritz!Box login. The service creates its IP phone
   in the Fritz!Box by itself (TR-064; confirm on the box if it asks for
   two-factor confirmation). You can also enter the SIP credentials of an IP
   phone you created yourself.
2. **PhoneBlock:** log in with your PhoneBlock account (free of charge). This
   gives the service the API key it needs to check callers.

Until the PhoneBlock login is set up, the web UI only accepts access from the
local network.

## Network

Use **host networking** (`--network host`): the Fritz!Box must reach the service
directly via SIP and RTP (UDP) at the host's address. The local ports are
`15060` (SIP) and `16000` (RTP) by default (settings `sip_lport` and `rtp_port`).

Without host networking you have to publish these UDP ports as well as the web
UI port, and set `contact_host`/`contact_port` in the settings to the address of
the Docker host.

## Configuration

All settings are made in the web UI; the quick start above needs no further
options. Only the time zone and, if needed, the web UI's port are set as
environment variables when the container is created:

| Variable | Default | Meaning |
| --- | --- | --- |
| `TZ` | UTC | time zone for logs and call times, e.g. `Europe/Berlin` |
| `PHONEBLOCK_WEB_PORT` | `8080` | port of the web UI |
| `PHONEBLOCK_WEB_BIND` | all addresses | address the web UI listens on |

With host networking the web UI uses the port directly on the Docker host. If
port 8080 is already taken there (e.g. by the NAS's own interface), choose
another one: add `-e PHONEBLOCK_WEB_PORT=8090` to the `docker run` command, or
with Compose:

```yaml
    environment:
      TZ: Europe/Berlin
      PHONEBLOCK_WEB_PORT: "8090"
```

The web UI is then at `http://<docker-host>:8090/`. Use these variables rather
than the `--web`/`--web-bind` options, so the container's health check finds
the web UI.

## Home Assistant

Opened through Home Assistant ingress, the PhoneBlock login cannot return to
the web UI by redirect. There the UI offers **PhoneBlock-Account
verknüpfen** instead: phoneblock.net opens in a new tab, you confirm the code
the web UI shows, and the service fetches its API key from PhoneBlock itself.

## Data

Settings, the device ID and the announcement are stored in the volume at
`/var/lib/phoneblock` (file `dongle.conf`). The service runs as user
`phoneblock` (UID 100, GID 101). On start, the container hands a mounted host
directory over to this user. If you don't want that, start the container with
`--user <uid>:<gid>` and a directory this user can write to.

## Updates

```sh
docker pull phoneblock/answerbot2:latest
docker rm -f phoneblock
# then run the docker run command from above again
```

With Compose: `docker compose pull && docker compose up -d`. The settings are
kept in the volume.

## Logs and health

```sh
docker logs -f phoneblock
```

The container reports `healthy` once the web UI responds at `/health`.

## Coming from `phoneblock/answerbot`

This image replaces the Java-based [`phoneblock/answerbot`](https://hub.docker.com/r/phoneblock/answerbot).
Its environment variables (`SIP_USER`, `PHONEBLOCK_API_KEY`, …) are not used any
more; set up the Fritz!Box and the PhoneBlock login again in the web UI. The
service answers spam calls and plays the announcement; it does not hold a
conversation with the caller like the old answerbot (yet).

## Links

- PhoneBlock: <https://phoneblock.net/phoneblock/>
- Source code and issues: <https://github.com/haumacher/phoneblock>
- Installing without Docker (Debian, Raspberry Pi OS): <https://haumacher.github.io/phoneblock/apt/>

License: GPL-3.0-or-later
