# Samanvay Tasklist: one jar serving the React app and the API.
# scripts/build.sh puts the prebuilt jar into dist/ so nothing is compiled on the server.
FROM eclipse-temurin:17-jre-noble

RUN command -v curl >/dev/null || (apt-get update \
      && apt-get install -y --no-install-recommends curl \
      && rm -rf /var/lib/apt/lists/*) \
  && useradd --system --uid 1001 samanvay \
  && mkdir /data && chown samanvay /data

COPY dist/app.jar /app/app.jar
USER samanvay
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
