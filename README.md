# Kotlin Vert.x Health Check Application

This is a simple backend application built with Kotlin and Vert.x.
It provides a basic health check endpoint.

## Prerequisites

- Java Development Kit (JDK) 11 or higher
- Gradle (usually bundled with the project via Gradle wrapper)

## Running the Application

To run the application, execute the following Gradle task:

```bash
./gradlew run
```

The server will start on port 8888 by default.

## API Endpoints

### Health Check

*   **Method:** `GET`
*   **Path:** `/healthz`
*   **Description:** Returns a 200 OK status and the plain text "OK" if the application is running and healthy.

    **Example Response:**
    ```
    HTTP/1.1 200 OK
    Content-Type: text/plain
    Content-Length: 2

    OK
    ```
