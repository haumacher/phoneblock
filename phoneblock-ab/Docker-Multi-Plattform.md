# Bereitstellen von Multi-Plattform Images
Docker unterstützt das Bereitstellen von Multi-Plattform Images.
Die Dokumentation hierfür ist unter https://docs.docker.com/build/building/multi-platform/ zu finden.

## Setup
Zunächst muss auf dem PC `docker`, `docker-buildx` und `binfmt-support `installiert sein. Nach der Installation von `docker-buildx` muss Docker neu gestartet werden.

### Ubuntu
`sudo apt install docker binfmt-support docker-buildx`

## Einrichten QEMU
QEMU wird benötigt, um die verschiedenen CPU-Architekturen zu emulieren.
Zum Installieren wird folgender Befehl ausgeführt:

`docker run --privileged --rm tonistiigi/binfmt --install all`

## Einrichten des Builders
Um mit `buildx` plattformunabhängig zu bauen, muss ein eigener Builder erzeugt werden.

`docker buildx create --name build_multi --driver docker-container`

## Bauen von multi-plattform Images
Zu beachten ist, dass auch die verwendeten Images für alle Ziel-Plattformen gebaut sein müssen. `eclipse-temurin' unterstützt mehrere Plattformen:
- `windows/amd64`
- `linux/amd64`
- `linux/arm/v7`
- `linux/arm64/v8`
- `linux/ppc64le`
- `linux/riscv64`
- `linux/s390x`

Um jetzt für mehrere Plattformen zu Bauen muss `buildx` mit dem zuvor definierten Builder ausgeführt werden. Die Plattformen werden kommasepariert angegeben. Mit `-t ` werden die Tags definiert.

`docker buildx build --platform linux/amd64,linux/arm/v7,linux/arm64/v8 --builder build_multi -t phoneblock/answerbot:latest .`

Hiernach sollten Images für `linux/amd64`, `linux/arm/v7` und `linux/arm64/v8` gebaut worden sein. Wird zusätzlich noch `--push` hinzugefügt, so werden die Images direkt auf Dockerhub veröffentlicht.