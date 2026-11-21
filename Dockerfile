# Pinned runtime lines; update intentionally and run the conformance tests before release.
FROM python:3.13-slim-bookworm@sha256:a5a7017efc5969e00ae2b91c8eb0631049a569652aa4ffc4501769049ae45c72 AS assets
WORKDIR /build
COPY profiles profiles
COPY scripts/prepare-assets.py scripts/prepare-assets.py
RUN python3 scripts/prepare-assets.py

FROM assets AS public-assets
COPY scripts/package-public-assets.py scripts/package-public-assets.py
RUN python3 scripts/package-public-assets.py service/assets

FROM maven:3.9.16-eclipse-temurin-25-noble@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /build/service
COPY service/pom.xml pom.xml
RUN mvn -B -q dependency:go-offline
COPY service/src src
COPY service/web web
COPY --from=assets /build/service/assets assets
COPY test/VitalSigns.xml /build/test/VitalSigns.xml
RUN mvn -B -q package

FROM eclipse-temurin:25.0.5.1_2-jre-noble@sha256:f3dd0838e69b881cc382237d5901e74ae3705b1056ad964b2e0e523b174fef99 AS runtime
WORKDIR /app
RUN groupadd --gid 10001 validator && useradd --uid 10001 --gid validator --no-create-home validator
COPY --from=build /build/service/target/cda-validator-service.jar app.jar
COPY --from=public-assets /build/service/assets assets
COPY service/web web
COPY LICENSE.md THIRD_PARTY_NOTICES.md ./
COPY licenses licenses
ENV HOST=0.0.0.0 PORT=17643 ASSET_DIR=/app/assets TERMINOLOGY_DIR=/app/terminology WEB_DIR=/app/web VALIDATION_WORKERS=2
USER 10001:10001
EXPOSE 17643
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
