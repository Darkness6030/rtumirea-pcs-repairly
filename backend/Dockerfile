ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -ntp -Dmaven.wagon.http.retryHandler.count=5 -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S repairly && adduser -S repairly -G repairly
WORKDIR /app
COPY --from=build /build/target/repairly-backend-1.0.0.jar app.jar
USER repairly
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
