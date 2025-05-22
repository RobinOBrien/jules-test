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

### Get Order Book

Retrieves the current state of the order book for the BTC/ZAR pair.

*   **Method:** `GET`
*   **Path:** `/BTCZAR/orderbook`
*   **Success Response:**
    *   **Code:** 200 OK
    *   **Content Example:**
        ```json
        {
          "Asks": [
            {
              "price": 101.50,
              "quantity": 5.0,
              "orderCount": 1
            }
          ],
          "Bids": [
            {
              "price": 100.00,
              "quantity": 10.0,
              "orderCount": 2
            }
          ]
        }
        ```

### Submit Limit Order

Places a new limit order into the order book.

*   **Method:** `POST`
*   **Path:** `/v1/orders/limit`
*   **Request Body Example:**
    ```json
    {
      "side": "BUY",
      "quantity": "2.5",
      "price": "99.50",
      "pair": "BTC/ZAR"
    }
    ```
    *   `side`: "BUY" or "SELL"
    *   `quantity`: Desired amount (string representation of a number)
    *   `price`: Price per unit (string representation of a number)
    *   `pair`: Currency pair (currently only "BTC/ZAR" is supported)
*   **Success Response:**
    *   **Code:** 201 Created
    *   **Content Example:**
        ```json
        {
          "orderId": "uuid-string-generated-by-server"
        }
        ```
*   **Error Responses:**
    *   **Code:** 400 Bad Request
        *   If input validation fails (e.g., invalid side, non-numeric quantity/price, unsupported pair, negative values).
        *   **Content Example:**
            ```json
            {
              "error": "Invalid quantity format: NOT_A_NUMBER"
            }
            ```
            ```json
            {
              "error": "Only BTC/ZAR pair is supported"
            }
            ```

### Get Recent Trades

Retrieves a list of recently executed trades for the BTC/ZAR pair. The history is limited to the last 100 trades.

*   **Method:** `GET`
*   **Path:** `/BTCZAR/tradehistory`
*   **Success Response:**
    *   **Code:** 200 OK
    *   **Content Example:**
        ```json
        [
          {
            "id": "trade-uuid-1",
            "currencyPair": "BTC/ZAR",
            "price": 100.00,
            "quantity": 1.5,
            "tradedAt": 1678886400000,
            "takerSide": "BUY",
            "sequenceId": 102
          },
          {
            "id": "trade-uuid-2",
            "currencyPair": "BTC/ZAR",
            "price": 99.90,
            "quantity": 0.5,
            "tradedAt": 1678886300000,
            "takerSide": "SELL",
            "sequenceId": 101
          }
        ]
        ```
