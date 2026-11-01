# The dongle's Linux service built from the working tree, for the ingress
# test setup (compose.yaml next to this file). The release image
# (../Dockerfile) installs the released APT package and so cannot carry an
# unreleased change; this one compiles firmware/linux the way debian/rules
# does and runs it with the same entrypoint.
#
# Build context: phoneblock-dongle/ (compose.yaml sets it). What is sent is
# limited by Dockerfile.dockerignore next to this file.

ARG PHONEBLOCK_SUITE=trixie

FROM debian:${PHONEBLOCK_SUITE} AS build

# The Build-Depends of debian/control, without the packaging tools.
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential pkgconf \
         libcjson-dev libsrtp2-dev libcurl4-openssl-dev libssl-dev \
         libmicrohttpd-dev python3 \
    && rm -rf /var/lib/apt/lists/*

# No git history in the context: the version in the User-Agent comes from here.
ARG PHONEBLOCK_VERSION=ingress-test

WORKDIR /src
COPY firmware/core firmware/core
COPY firmware/main firmware/main
COPY firmware/linux firmware/linux
COPY firmware/i18n/l10n/ui/ui_en.arb firmware/i18n/l10n/ui/ui_en.arb
RUN make -C firmware/linux -j"$(nproc)" phoneblock_linux \
      CFLAGS="-O2 -g -Wall" PHONEBLOCK_VERSION="${PHONEBLOCK_VERSION}"

FROM debian:${PHONEBLOCK_SUITE}-slim

# The runtime libraries of the release image (../Dockerfile), the service
# user with the same fixed ids, curl for the health check.
RUN groupadd --system --gid 101 phoneblock \
    && useradd --system --uid 100 --gid phoneblock --home-dir /var/lib/phoneblock \
         --no-create-home --shell /usr/sbin/nologin phoneblock \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl tzdata \
         libcjson1 libcurl4t64 libmicrohttpd12t64 libsrtp2-1 libssl3t64 \
    && rm -rf /var/lib/apt/lists/*

# The package layout (debian/rules override_dh_install).
COPY --from=build /src/firmware/build/linux-host/phoneblock-dongle /usr/bin/phoneblock-dongle
COPY --from=build /src/firmware/build/linux-host/web/ /usr/share/phoneblock-dongle/web/
COPY debian/examples/dongle.conf.example /usr/share/phoneblock-dongle/dongle.conf.example
COPY docker/entrypoint.sh /usr/local/bin/phoneblock-dongle-entrypoint
COPY docker/ingress-test/seed-config.sh /usr/local/bin/phoneblock-seed-config

ENV PHONEBLOCK_PLATFORM="Linux; Ingress-Test"

VOLUME /var/lib/phoneblock
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
    CMD curl -fsS "http://127.0.0.1:8080/health" >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/phoneblock-seed-config"]
