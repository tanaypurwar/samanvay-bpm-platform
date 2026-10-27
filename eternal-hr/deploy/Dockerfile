# Eternal HR process application. scripts/build.sh puts the jar in dist/ so nothing is compiled on the server.
FROM eclipse-temurin:17-jre-noble

RUN command -v curl >/dev/null || (apt-get update \
      && apt-get install -y --no-install-recommends curl \
      && rm -rf /var/lib/apt/lists/*) \
  && useradd --system --uid 1001 eternalhr

COPY dist/eternal-hr.jar /app/eternal-hr.jar
USER eternalhr
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/eternal-hr.jar"]
