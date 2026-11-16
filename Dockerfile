# Pinned runtime lines; update intentionally and run the conformance tests before release.
FROM python:3.13-slim-bookworm@sha256:a5a7017efc5969e00ae2b91c8eb0631049a569652aa4ffc4501769049ae45c72 AS assets
WORKDIR /build
COPY profiles profiles
COPY scripts/prepare-assets.py scripts/prepare-assets.py
RUN python3 scripts/prepare-assets.py

FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /build/service
COPY service/pom.xml pom.xml
RUN mvn -B -q dependency:go-offline
COPY service/src src
COPY service/web web
COPY --from=assets /build/service/assets assets
COPY test/VitalSigns.xml /build/test/VitalSigns.xml
RUN mvn -B -q package

FROM eclipse-temurin:21-jre-jammy@sha256:dff6eb46527bd293a3c06bbbe9e64e8c9f91f01905b5abbdad31761c03059d2c AS runtime
WORKDIR /app
RUN groupadd --gid 10001 validator && useradd --uid 10001 --gid validator --no-create-home validator
COPY --from=build /build/service/target/cda-validator-service.jar app.jar
COPY --from=assets /build/service/assets assets
COPY service/web web
COPY LICENSE.md THIRD_PARTY_NOTICES.md ./
COPY licenses licenses
ENV HOST=0.0.0.0 PORT=8080 ASSET_DIR=/app/assets WEB_DIR=/app/web VALIDATION_WORKERS=2
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
