FROM eclipse-temurin:17
LABEL org.opencontainers.image.authors=play@haumacher.de
USER root
RUN adduser --system  --uid 999 --group --home /opt/phoneblock phoneblock

USER phoneblock
RUN mkdir  /opt/phoneblock/bin /opt/phoneblock/conversation /opt/phoneblock/recordings
COPY --chown=phoneblock:phoneblock .phoneblock.docker /opt/phoneblock/.phoneblock
COPY --chown=phoneblock:phoneblock target/phoneblock-ab-*-jar-with-dependencies.jar /opt/phoneblock/bin/phoneblock-ab.jar

WORKDIR /opt/phoneblock/
ENTRYPOINT ["java", "-jar", "/opt/phoneblock/bin/phoneblock-ab.jar"]
CMD ["-f", "/opt/phoneblock/.phoneblock"]
EXPOSE 50060/tcp
EXPOSE 50060/udp
EXPOSE 50100-50109/udp
