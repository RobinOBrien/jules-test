package com.example

import com.example.orderbook.LimitOrderRequest // For request body
import com.example.orderbook.LimitOrderResponse
import com.example.orderbook.OrderBookResponse
import com.example.orderbook.Trade
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.predicate.ResponsePredicate
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal

@ExtendWith(VertxExtension::class)
class MainVerticleOrderBookApiTest {

    private val port = 8888 // Assuming MainVerticle still uses this port

    @BeforeEach
    fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        // Deploy a new MainVerticle for each test to ensure isolation
        vertx.deployVerticle(MainVerticle(), testContext.succeeding<String> { _ -> testContext.completeNow() })
    }

    @Test
    fun `GET BTCZAR orderbook initially empty`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        client.get(port, "localhost", "/BTCZAR/orderbook")
            .expect(ResponsePredicate.SC_OK)
            .expect(ResponsePredicate.contentType("application/json"))
            .send(testContext.succeeding { response ->
                testContext.verify {
                    val orderBook = response.bodyAsPojo(OrderBookResponse::class.java)
                    assertTrue(orderBook.Asks.isEmpty())
                    assertTrue(orderBook.Bids.isEmpty())
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `POST limit buy order then GET orderbook`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        val orderRequest = LimitOrderRequest(side = "BUY", quantity = "1.0", price = "100.00", pair = "BTC/ZAR")

        client.post(port, "localhost", "/v1/orders/limit")
            .expect(ResponsePredicate.SC_CREATED) // 201
            .expect(ResponsePredicate.contentType("application/json"))
            .sendJson(orderRequest, testContext.succeeding { postResponse ->
                testContext.verify {
                    val orderResponse = postResponse.bodyAsPojo(LimitOrderResponse::class.java)
                    assertNotNull(orderResponse.orderId)

                    // Now get the order book
                    client.get(port, "localhost", "/BTCZAR/orderbook")
                        .expect(ResponsePredicate.SC_OK)
                        .send(testContext.succeeding { getResponse ->
                            testContext.verify {
                                val orderBook = getResponse.bodyAsPojo(OrderBookResponse::class.java)
                                assertEquals(1, orderBook.Bids.size)
                                assertTrue(orderBook.Asks.isEmpty())
                                assertEquals(BigDecimal("100.00"), orderBook.Bids[0].price)
                                assertEquals(BigDecimal("1.0"), orderBook.Bids[0].quantity)
                                testContext.completeNow()
                            }
                        })
                }
            })
    }
    
    @Test
    fun `POST limit sell order then GET orderbook`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        val orderRequest = LimitOrderRequest(side = "SELL", quantity = "0.5", price = "101.00", pair = "BTC/ZAR")

        client.post(port, "localhost", "/v1/orders/limit")
            .expect(ResponsePredicate.SC_CREATED)
            .sendJson(orderRequest, testContext.succeeding { postResponse ->
                testContext.verify {
                    assertNotNull(postResponse.bodyAsPojo(LimitOrderResponse::class.java).orderId)
                    client.get(port, "localhost", "/BTCZAR/orderbook")
                        .expect(ResponsePredicate.SC_OK)
                        .send(testContext.succeeding { getResponse ->
                            testContext.verify {
                                val orderBook = getResponse.bodyAsPojo(OrderBookResponse::class.java)
                                assertEquals(1, orderBook.Asks.size)
                                assertTrue(orderBook.Bids.isEmpty())
                                assertEquals(BigDecimal("101.00"), orderBook.Asks[0].price)
                                assertEquals(BigDecimal("0.5"), orderBook.Asks[0].quantity)
                                testContext.completeNow()
                            }
                        })
                }
            })
    }

    @Test
    fun `POST buy and sell orders that match, then GET tradehistory and orderbook`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        val sellRequest = LimitOrderRequest(side = "SELL", quantity = "1.0", price = "100.00", pair = "BTC/ZAR")
        val buyRequest = LimitOrderRequest(side = "BUY", quantity = "1.0", price = "100.00", pair = "BTC/ZAR")

        // Post Sell Order (Maker)
        client.post(port, "localhost", "/v1/orders/limit").sendJson(sellRequest, testContext.succeeding { _ ->
            // Post Buy Order (Taker)
            client.post(port, "localhost", "/v1/orders/limit").sendJson(buyRequest, testContext.succeeding { _ ->
                // Check Trade History
                client.get(port, "localhost", "/BTCZAR/tradehistory")
                    .expect(ResponsePredicate.SC_OK)
                    .expect(ResponsePredicate.contentType("application/json"))
                    .send(testContext.succeeding { historyResponse ->
                        testContext.verify {
                            val trades = historyResponse.bodyAs(Array<Trade>::class.java)
                            assertEquals(1, trades.size)
                            assertEquals(BigDecimal("1.0"), trades[0].quantity)
                            assertEquals(BigDecimal("100.00"), trades[0].price)
                            assertEquals(com.example.orderbook.Side.BUY, trades[0].takerSide)
                            assertEquals("BTC/ZAR", trades[0].currencyPair)

                            // Check Order Book (should be empty)
                            client.get(port, "localhost", "/BTCZAR/orderbook")
                                .expect(ResponsePredicate.SC_OK)
                                .send(testContext.succeeding { bookResponse ->
                                    testContext.verify {
                                        val orderBook = bookResponse.bodyAsPojo(OrderBookResponse::class.java)
                                        assertTrue(orderBook.Asks.isEmpty())
                                        assertTrue(orderBook.Bids.isEmpty())
                                        testContext.completeNow()
                                    }
                                })
                        }
                    })
            })
        })
    }

    @Test
    fun `POST limit order with invalid pair returns 400`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        val orderRequest = LimitOrderRequest(side = "BUY", quantity = "1.0", price = "100.00", pair = "ETH/ZAR")

        client.post(port, "localhost", "/v1/orders/limit")
            .expect(ResponsePredicate.SC_BAD_REQUEST) // 400
            .expect(ResponsePredicate.contentType("application/json"))
            .sendJson(orderRequest, testContext.succeeding { response ->
                testContext.verify {
                    val error = response.bodyAsJsonObject()
                    assertTrue(error.getString("error").contains("Only BTC/ZAR pair is supported"))
                    testContext.completeNow()
                }
            })
    }
    
    @Test
    fun `POST limit order with invalid quantity string returns 400`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        // Constructing JsonObject manually because LimitOrderRequest expects String,
        // but we want to send a malformed one that OrderBookService would reject.
        // Here, the type is fine, but OrderBookService validation will fail.
        val orderRequestPayload = JsonObject()
            .put("side", "BUY")
            .put("quantity", "NOT_A_NUMBER")
            .put("price", "100.00")
            .put("pair", "BTC/ZAR")

        client.post(port, "localhost", "/v1/orders/limit")
            .expect(ResponsePredicate.SC_BAD_REQUEST)
            .expect(ResponsePredicate.contentType("application/json"))
            .sendJsonObject(orderRequestPayload, testContext.succeeding { response ->
                testContext.verify {
                    val error = response.bodyAsJsonObject()
                    assertTrue(error.getString("error").contains("Invalid quantity format"))
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `POST limit order with negative price returns 400`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
         val orderRequestPayload = JsonObject()
            .put("side", "BUY")
            .put("quantity", "10.0")
            .put("price", "-100.00") // Service layer should catch this
            .put("pair", "BTC/ZAR")

        client.post(port, "localhost", "/v1/orders/limit")
            .expect(ResponsePredicate.SC_BAD_REQUEST)
            .expect(ResponsePredicate.contentType("application/json"))
            .sendJsonObject(orderRequestPayload, testContext.succeeding { response ->
                testContext.verify {
                    val error = response.bodyAsJsonObject()
                    assertTrue(error.getString("error").contains("Price must be positive"))
                    testContext.completeNow()
                }
            })
    }
    
    @Test
    fun `POST limit order with malformed JSON returns 400`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        val malformedJson = "{\"side\": \"BUY\", \"quantity\": \"1.0\", \"price\": \"100.00\", \"pair\": \"BTC/ZAR\"" // Missing closing brace 

        client.post(port, "localhost", "/v1/orders/limit")
            .putHeader("content-type", "application/json") // Explicitly set, though sendJson usually does
            .expect(ResponsePredicate.SC_BAD_REQUEST)
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(malformedJson), testContext.succeeding { response ->
                 testContext.verify {
                    val error = response.bodyAsJsonObject()
                    // Error message for DecodeException was "Invalid JSON format: ..." in MainVerticle
                    assertTrue(error.getString("error").contains("Invalid JSON format"))
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `GET tradehistory initially empty`(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        client.get(port, "localhost", "/BTCZAR/tradehistory")
            .expect(ResponsePredicate.SC_OK)
            .expect(ResponsePredicate.contentType("application/json"))
            .send(testContext.succeeding { response ->
                testContext.verify {
                    val trades = response.bodyAs(Array<Trade>::class.java)
                    assertTrue(trades.isEmpty())
                    testContext.completeNow()
                }
            })
    }
}
