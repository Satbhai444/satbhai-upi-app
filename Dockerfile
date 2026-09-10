FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac -encoding utf8 -cp ".:sqlite-jdbc.jar" *.java
EXPOSE 8080
CMD ["java", "-cp", ".:sqlite-jdbc.jar", "BankApiServer"]
