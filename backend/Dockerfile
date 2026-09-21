# build stage: ใช้ Maven Wrapper ในโปรเจกต์ ไม่ต้องมี Maven ใน image
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
# คัดลอก wrapper กับ pom ก่อนเพื่อให้ layer ของ dependency ถูก cache ไว้
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
