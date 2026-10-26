# Samanvay Run (the branded Camunda Run built from distro/run) on its bundled H2 database.
# scripts/build.sh puts the prebuilt artifacts into dist/ so nothing is compiled on the server.
FROM eclipse-temurin:17-jre-noble

RUN command -v curl >/dev/null || (apt-get update \
      && apt-get install -y --no-install-recommends curl \
      && rm -rf /var/lib/apt/lists/*) \
  && useradd --system --uid 1001 --home-dir /opt/samanvay-run samanvay

WORKDIR /opt/samanvay-run
COPY dist/samanvay-run.tar.gz /tmp/samanvay-run.tar.gz
RUN tar xzf /tmp/samanvay-run.tar.gz -C /opt/samanvay-run && rm /tmp/samanvay-run.tar.gz

COPY default.yml configuration/default.yml
# BPMN files here are deployed automatically on startup
COPY dist/resources/ configuration/resources/
# /data holds the H2 database (a volume)
RUN mkdir /data && chown -R samanvay /opt/samanvay-run /data

USER samanvay
EXPOSE 8080
ENTRYPOINT ["./internal/run.sh", "start", "--webapps", "--rest"]
