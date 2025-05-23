# Use the official OpenJDK 24 image (or a compatible base image) as the parent image
FROM openjdk:24-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the jar file into the container
COPY  build/libs/app.jar app.jar

# Expose the port your app runs on (usually 8080)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
