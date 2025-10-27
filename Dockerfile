# ====== 1️⃣ Build stage ======
FROM gradle:8.10.2-jdk21 AS builder
# (⚠️ gradle-jdk24 n’existe pas encore, mais JDK 21 est LTS et compatible pour builder ton app)

WORKDIR /app

# Copier uniquement les fichiers de config Gradle pour optimiser le cache
COPY build.gradle settings.gradle gradlew* ./
COPY gradle ./gradle

# Télécharge les dépendances (cache optimisé)
RUN ./gradlew dependencies || return 0

# Copier le reste du projet
COPY . .

# Build le JAR (sans tests pour gagner du temps en CI/CD)
RUN ./gradlew clean build -x test #

# ====== 2️⃣ Runtime stage ======
FROM openjdk:21-jdk-slim

WORKDIR /app

# Copier le jar généré depuis le builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose le port (par défaut 8080 pour Spring Boot)
EXPOSE 8080

# Lancer l'application
ENTRYPOINT ["java", "--add-opens=java.base/java.time=ALL-UNNAMED", "-jar", "app.jar"]
