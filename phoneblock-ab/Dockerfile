# ===== Stage 1: Build the JAR using Maven =====
FROM maven:3.9.9-eclipse-temurin-17 AS builder

ARG GITHUB_USER
ARG GITHUB_TOKEN

# Set the working directory
WORKDIR /build

# Configure Maven settings for GitHub Packages
RUN mkdir -p /root/.m2
RUN echo "<settings>\
  <servers>\
    <server>\
      <id>github</id>\
      <username>${GITHUB_USER}</username>\
      <password>${GITHUB_TOKEN}</password>\
    </server>\
  </servers>\
</settings>" > /root/.m2/settings.xml

# Clone and install the missing dependency, see https://github.com/haumacher/phoneblock/issues/202 ---
RUN git clone https://github.com/haumacher/java-utils-mail-dkim.git /tmp/java-utils-mail-dkim && \
    cd /tmp/java-utils-mail-dkim && \
    sed -i 's/<version>3.2.0<\/version>/<version>3.2.1-haumacher<\/version>/' pom.xml && \
    mvn clean install -DskipTests -Dspotbugs.skip=true && \
    rm -rf /tmp/java-utils-mail-dkim

# Copy the whole project
COPY . .

# Pre-fetch dependencies (speeds up rebuilds)
RUN mvn dependency:go-offline -B

# Build the project and package the jar with dependencies
RUN mvn -am --projects de.haumacher:phoneblock-ab package -DskipTests

# ===== Stage 2: Runtime image =====
FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.authors="play@haumacher.de"

USER root
RUN adduser --system  --uid 999 --group --home /opt/phoneblock phoneblock

USER phoneblock
RUN mkdir -p /opt/phoneblock/bin /opt/phoneblock/conversation /opt/phoneblock/recordings
COPY --from=builder --chown=phoneblock:phoneblock /build/phoneblock-ab/target/*-jar-with-dependencies.jar /opt/phoneblock/bin/
COPY --chown=phoneblock:phoneblock phoneblock-ab/.phoneblock.docker /opt/phoneblock/.phoneblock

RUN for f in /opt/phoneblock/bin/*-jar-with-dependencies.jar; do \
        mv "$f" /opt/phoneblock/bin/phoneblock-ab.jar; \
    done

WORKDIR /opt/phoneblock/
ENTRYPOINT ["java", "-jar", "/opt/phoneblock/bin/phoneblock-ab.jar"]
CMD ["-f", "/opt/phoneblock/.phoneblock"]
EXPOSE 50060/tcp
EXPOSE 50060/udp
EXPOSE 50100-50109/udp
