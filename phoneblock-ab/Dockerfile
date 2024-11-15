FROM eclipse-temurin:17
MAINTAINER play@haumacher.de
USER root
RUN mkdir /opt/phoneblock
RUN mkdir /opt/phoneblock/bin
RUN mkdir /opt/phoneblock/conversation
RUN mkdir /opt/phoneblock/recordings
COPY .phoneblock.docker /opt/phoneblock/.phoneblock
COPY target/phoneblock-ab-1.5.0-SNAPSHOT-jar-with-dependencies.jar /opt/phoneblock/bin/phoneblock-ab.jar
CMD ["java", "-jar", "/opt/phoneblock/bin/phoneblock-ab.jar", "-f", "/opt/phoneblock/.phoneblock"]
EXPOSE 50060/tcp
EXPOSE 50060/udp
EXPOSE 50100/udp
EXPOSE 50101/udp
EXPOSE 50102/udp
EXPOSE 50103/udp
EXPOSE 50104/udp
EXPOSE 50105/udp
EXPOSE 50106/udp
EXPOSE 50107/udp
EXPOSE 50108/udp
EXPOSE 50109/udp
