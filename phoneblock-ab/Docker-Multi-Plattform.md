# Bereitstellen von Multi-Plattform Images
Docker unterstützt das Bereitstellen von Multi-Plattform Images.
Die Dokumentation hierfür ist unter https://docs.docker.com/build/building/multi-platform/ zu finden.

## Setup
Zunächst muss auf dem PC `docker`, `docker-buildx` und `binfmt-support `installiert sein. Nach der installation von `docker-buildx` muss Docker neu gestartet werden.
### Ubuntu
`sudo apt install docker binfmt-support docker-buildx`

## Einrichten QEMU
QEMU wird benötigt um die verschiedenen CPU-Architekturen zu emulieren.
Zum Installieren wird folgender Befehl ausgeführt:

`docker run --privileged --rm tonistiigi/binfmt --install all`

## Einrichten des Builders
Um mit `buildx` plattformunabhängig zu bauen, muss ein eigener Builder erzeugt werden.

`docker buildx create --name my_builder --driver docker-container`

## Bauen von Multi-Plattform Images
ZU beachten ist, dass die verwendeten Images ebenfalls die für die Plattformen gebaut sein müssen. `eclipse-temurin' unterstützt mehrere Plattformen:
- windows/amd64
- windows/amd64
- linux/amd64
- linux/arm/v7
- linux/arm64/v8
- linux/ppc64le
- linux/riscv64
- linux/s390x

Um jetzt für mehrere Plattformen zu Bauen muss `buildx` mit dem zuvor definierten Builder ausgeführt werden. Die plattformen werden kommasepariert angegeben. Mit `-t ` werden die Tags definiert.

`docker buildx build --platform linux/amd64,linux/arm/v7,linux/arm64/v8 --builder my_builder -t pb_test .`

Hiernach sollten images für linux/amd64, linux/arm/v7 und linux/arm64/v8 gebaut worden sein. Wird zusätzlich nach `--push` hinzugefügt, so werden die Images direkt auf Dockerhub veröffentlicht.