# Ingress test setup ("the cookie-swallowing firewall")

Reproduces the Home Assistant situation of #619 without Home Assistant, to
try the device-code link of #621 (and to see why the redirect sign-in fails
there).

| Service | Host port | What |
| --- | --- | --- |
| `ingress` | `8133` (`INGRESS_PORT`) | nginx imitating HA ingress: shell page at `/`, the service in an iframe at `/api/hassio_ingress/pbtest-ingress-token/` |
| `ingress` | `8134` (`FOREIGN_PORT`) | a page on "another site" with a link into the ingress prefix |
| `phoneblock` | `8098` (`SERVICE_PORT`) | the Linux service **built from this working tree**, reached directly |
| `fake-phoneblock` | `8095` (`FAKE_PORT`) | only with `--profile fake`: a stand-in for phoneblock.net's link endpoints |

(HA itself uses 8123; the ports here avoid it and the local Jetty on 8080.
Keep `FOREIGN_PORT` one above `INGRESS_PORT`, the shell links to it that way.)

## What it emulates

- `/` sets the session cookie `ingress_session` with `SameSite=Strict`,
  `HttpOnly`, path `/api/hassio_ingress/` — like HA.
- Below `/api/hassio_ingress/pbtest-ingress-token/` a request **without** that
  cookie gets `401: Unauthorized`. **With** it the request goes to the
  service with the prefix stripped and `X-Ingress-Path:
  /api/hassio_ingress/pbtest-ingress-token` set; the bare prefix redirects to
  its slash form. Any other token is a 401.
- A browser leaves a `SameSite=Strict` cookie off every navigation another
  site started. So the redirect from phoneblock.net back into the prefix is
  refused, exactly as in HA (#619, obstacle 3), while the same URL pasted
  into the address bar works.

The service runs with SIP unconfigured. Its PhoneBlock server (`pb_url` in
`dongle.conf`) is set on every start from `PHONEBLOCK_BASE_URL`
(`seed-config.sh`); the state lives in the volume `phoneblock-state`.

## Start

Needs Docker with the Compose plugin (`docker compose`). From this directory:

```sh
# Against a local Jetty on the Docker host (cd phoneblock && mvn jetty:run):
docker compose up --build -d

# Against the test server:
PHONEBLOCK_BASE_URL=https://phoneblock.net/pb-test docker compose up --build -d

# Without any server: fake-phoneblock.py (confirm at http://localhost:8095/phoneblock/link,
# or FAKE_AUTO_APPROVE=2 to approve at the second poll):
PHONEBLOCK_BASE_URL=http://fake-phoneblock:8090/phoneblock \
  docker compose --profile fake up --build -d

docker compose logs -f phoneblock      # the service log
docker compose --profile fake down -v  # stop, drop the state
```

The default `PHONEBLOCK_BASE_URL` is `http://host.docker.internal:8080/phoneblock`
(mapped to the host by `extra_hosts: host-gateway`). Jetty must listen on all
addresses (its default). The confirmation link the service shows is the
server's `verifyUrl`, built from the server's own configured public URL — for
a local Jetty usually `http://localhost:8080/phoneblock`, so open the shell in
a browser on the same machine.

`--build` rebuilds the service from the working tree; run it again after a
firmware change.

## The two test paths

Open everything under the **LAN IP** of the Docker host (`hostname -I`), not
under `localhost`: the cross-site check below needs the shell on a different
host name than the "foreign site" and the server.

1. **Through the proxy — device-code link:** `http://<LAN-IP>:8133/`. The
   setup step and the token page show **PhoneBlock-Account verknüpfen**
   (`/api/status` reports `phoneblock.token_link: true`, `token_signin:
   false`). The click opens a tab with the server's confirmation page and
   shows the code large; confirm it there (or enter it at `<server>/link`).
   Within one poll interval (5 s) the setup step turns green. Declining
   shows the reason and a retry button; an unconfirmed code expires after
   10 minutes.
2. **Directly — unchanged redirect sign-in:** `http://<LAN-IP>:8098/`. No
   `X-Ingress-Path`, so `token_link` is absent, `token_signin: true`, and
   the token page shows the usual "Bei PhoneBlock anmelden" redirect flow
   (`/register-start` → login → `/token-callback`).

## Reproducing obstacle 3 (the 401)

1. Open the shell `http://<LAN-IP>:8133/` once (sets the cookie).
2. In the shell's sidebar follow **Fremde Seite** — it opens
   `http://localhost:8134/` with the ingress URL filled in. Follow the link
   there: **`401: Unauthorized`**. That is what the redirect from
   phoneblock.net to `…/api/hassio_ingress/<token>/token-callback` gets.
3. Paste the same URL into the address bar: the web UI appears.

Sites are compared by host name, not port: with the shell under
`localhost` the "foreign" page is the same site and the cookie goes along.

The old flow itself cannot be completed through the proxy in this firmware
either: `/register-start` builds the callback from the `Host` header without
the ingress prefix, and the server accepts only plain-`http` LAN hosts as
callbacks (obstacle 2). That is why the UI hides it under ingress.

## Quick checks with curl

```sh
P=/api/hassio_ingress/pbtest-ingress-token
curl -i http://localhost:8133$P/api/status                  # 401: Unauthorized
curl -s -c jar http://localhost:8133/ >/dev/null            # take the cookie
curl -s -b jar http://localhost:8133$P/api/status           # ..."token_link":true...
curl -s http://localhost:8098/api/status                    # no token_link, "token_signin":true
curl -s -b jar -X POST http://localhost:8133$P/api/link/start   # {userCode, verifyUrl, expiresIn}
curl -s -b jar http://localhost:8133$P/api/link/status      # {state, userCode, verifyUrl, message}
```
