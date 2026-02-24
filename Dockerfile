FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jdk
WORKDIR /app

RUN mkdir -p /data/wal /data/wal/archive

COPY --from=builder /app/target/wal-engine-1.0.0.jar app.jar

VOLUME ["/data/wal"]

ENV WAL_DIR=/data/wal
ENV WAL_ROLE=PRIMARY
ENV WAL_SEGMENT_SIZE_MB=64
ENV WAL_GRPC_PORT=9090
ENV WAL_COMPACTION_ENABLED=true
ENV HTTP_PORT=8080

EXPOSE 8080 9090

#ENTRYPOINT ["java", \
#  "-XX:+UseG1GC", \
#  "-XX:MaxGCPauseMillis=50", \
#  "-XX:+HeapDumpOnOutOfMemoryError", \
#  "-Xms128m", "-Xmx512m", \
#  "-jar", "app.jar"]

ENTRYPOINT ["java", \
  "-XX:+UseSerialGC", \
  "-XX:MaxRAM=400m", \
  "-Xms64m", "-Xmx350m", \
  "-XX:+UseCompressedOops", \
  "-Dspring.jmx.enabled=false", \
  "-jar", "app.jar"]