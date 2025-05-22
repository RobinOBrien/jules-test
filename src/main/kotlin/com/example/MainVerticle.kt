package com.example

import com.example.orderbook.OrderBookService
import com.example.orderbook.LimitOrderRequest
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.Json
import io.vertx.core.json.DecodeException
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler

class MainVerticle : AbstractVerticle() {

  private val orderBookService = OrderBookService()

  override fun start(startPromise: Promise<Void>) {
    val router = Router.router(vertx)

    // Mount BodyHandler globally or before specific routes needing it.
    // Placing it before all routes that might need it.
    router.route().handler(BodyHandler.create())

    // Health check endpoint (existing)
    router.get("/healthz").handler { context ->
      context.response()
        .putHeader("content-type", "text/plain")
        .setStatusCode(200)
        .end("OK")
    }

    // Order Book Endpoints
    router.get("/BTCZAR/orderbook").handler { context ->
      try {
        val orderBookView = orderBookService.getOrderBookView()
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(200)
          .end(Json.encodePrettily(orderBookView))
      } catch (e: Exception) {
        // Log the exception server-side for diagnostics
        // For the client, a generic 500 error is often appropriate
        println("Error retrieving order book: ${e.message}")
        e.printStackTrace() // For server logs
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(500)
          .end("{\"error\": \"Error retrieving order book\"}")
      }
    }

    router.post("/v1/orders/limit").handler { context ->
      try {
        // Attempt to parse the JSON body into a LimitOrderRequest object
        val orderRequest = context.body().asPojo(LimitOrderRequest::class.java)

        // Validate that the currency pair is BTC/ZAR as per current service limitation
        // Note: LimitOrderRequest has 'pair', Order.kt has 'currencyPair'.
        // The OrderBookService currently hardcodes "BTC/ZAR" in the Order constructor default
        // if not overridden. This check ensures the request explicitly states the supported pair.
        if (orderRequest.pair.uppercase() != "BTC/ZAR") {
             context.response()
              .putHeader("content-type", "application/json")
              .setStatusCode(400)
              .end(Json.encodePrettily(mapOf("error" to "Only BTC/ZAR pair is supported")))
            return@handler
        }

        val orderResponse = orderBookService.submitOrder(orderRequest)
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(201) // 201 Created for successful resource creation
          .end(Json.encodePrettily(orderResponse))
      } catch (e: DecodeException) { // Specifically for JSON parsing errors
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(400)
          .end(Json.encodePrettily(mapOf("error" to "Invalid JSON format: ${e.message}")))
      } catch (e: IllegalArgumentException) { // From OrderBookService validation
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(400)
          .end(Json.encodePrettily(mapOf("error" to e.message)))
      } catch (e: Exception) { // Catch-all for other unexpected errors
        println("Error processing order: ${e.message}")
        e.printStackTrace() // For server logs
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(500) // Internal Server Error for unexpected issues
          .end(Json.encodePrettily(mapOf("error" to "Failed to process order")))
      }
    }

    router.get("/BTCZAR/tradehistory").handler { context ->
      try {
        val tradeHistory = orderBookService.getRecentTrades()
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(200)
          .end(Json.encodePrettily(tradeHistory))
      } catch (e: Exception) {
        println("Error retrieving trade history: ${e.message}")
        e.printStackTrace() // For server logs
        context.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(500)
          .end("{\"error\": \"Error retrieving trade history\"}")
      }
    }

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888) { http ->
        if (http.succeeded()) {
          startPromise.complete()
          println("HTTP server started on port 8888")
        } else {
          startPromise.fail(http.cause());
        }
      }
  }
}
