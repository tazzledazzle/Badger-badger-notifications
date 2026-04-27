# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :gateway:installDist :worker:installDist --no-daemon

FROM eclipse-temurin:17-jre AS gateway
COPY --from=build /workspace/gateway/build/install/gateway /opt/badger-gateway
ENV JDBC_URL=jdbc:postgresql://postgres:5432/badger
ENV JDBC_USER=badger
ENV JDBC_PASSWORD=badger
ENV REDIS_URL=redis://redis:6379
ENV GATEWAY_API_KEY=dev-key
EXPOSE 8080
CMD ["/opt/badger-gateway/bin/gateway"]

FROM eclipse-temurin:17-jre AS worker
COPY --from=build /workspace/worker/build/install/worker /opt/badger-worker
ENV JDBC_URL=jdbc:postgresql://postgres:5432/badger
ENV JDBC_USER=badger
ENV JDBC_PASSWORD=badger
ENV REDIS_URL=redis://redis:6379
ENV WORKER_CONSUMER_ID=worker-1
EXPOSE 9404
CMD ["/opt/badger-worker/bin/worker"]
