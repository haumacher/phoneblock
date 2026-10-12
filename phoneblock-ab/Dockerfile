FROM eclipse-temurin:17
LABEL org.opencontainers.image.authors=play@haumacher.de

# gosu is used by the entrypoint to drop from root to the phoneblock user
# after applying the PUID/PGID overrides.
RUN apt-get update \
 && apt-get install -y --no-install-recommends gosu \
 && rm -rf /var/lib/apt/lists/* \
 && adduser --system --uid 999 --group --home /opt/phoneblock phoneblock \
 && install -d -o phoneblock -g phoneblock /opt/phoneblock/bin /opt/phoneblock/conversation /opt/phoneblock/recordings
# Directories are created with their owner instead of `mkdir` + `chown -R`: the recursive chown
# has to read the directory, which fails on linux/arm/v7 under QEMU emulation with "Value too
# large for defined data type" (32-bit stat on 64-bit inodes).

COPY --chown=phoneblock:phoneblock .phoneblock.docker /opt/phoneblock/.phoneblock
COPY --chown=phoneblock:phoneblock target/phoneblock-ab-*-jar-with-dependencies.jar /opt/phoneblock/bin/phoneblock-ab.jar
# The default conversation (the PCMA/PCMA-WB fragments, see .dockerignore) is part of the image;
# a bind mount on /opt/phoneblock/conversation replaces it with a custom one (#586).
COPY --chown=phoneblock:phoneblock conversation/ /opt/phoneblock/conversation/
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

WORKDIR /opt/phoneblock/
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["-f", "/opt/phoneblock/.phoneblock"]
EXPOSE 50060/tcp
EXPOSE 50060/udp
EXPOSE 50100-50109/udp
