package com.example.orderbook

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderBookServiceTest {

    private lateinit var service: OrderBookService

    @BeforeEach
    fun setUp() {
        service = OrderBookService()
    }

    private fun createOrderRequest(side: String, price: String, quantity: String, pair: String = "BTC/ZAR"): LimitOrderRequest {
        return LimitOrderRequest(side = side, price = price, quantity = quantity, pair = pair)
    }

    @Test
    fun `submit new buy order, no match, adds to buy orders`() {
        val request = createOrderRequest("BUY", "100.00", "10")
        val response = service.submitOrder(request)
        assertNotNull(response.orderId)

        val bookView = service.getOrderBookView()
        assertEquals(1, bookView.Bids.size)
        assertEquals(0, bookView.Asks.size)
        assertEquals(BigDecimal("100.00"), bookView.Bids[0].price)
        assertEquals(BigDecimal("10"), bookView.Bids[0].quantity)
        assertEquals(1, bookView.Bids[0].orderCount)
        assertTrue(service.getRecentTrades().isEmpty())
    }

    @Test
    fun `submit new sell order, no match, adds to sell orders`() {
        val request = createOrderRequest("SELL", "101.00", "5")
        val response = service.submitOrder(request)
        assertNotNull(response.orderId)

        val bookView = service.getOrderBookView()
        assertEquals(0, bookView.Bids.size)
        assertEquals(1, bookView.Asks.size)
        assertEquals(BigDecimal("101.00"), bookView.Asks[0].price)
        assertEquals(BigDecimal("5"), bookView.Asks[0].quantity)
        assertEquals(1, bookView.Asks[0].orderCount)
        assertTrue(service.getRecentTrades().isEmpty())
    }

    @Test
    fun `submit buy order, full match with one sell order`() {
        // Maker (Sell)
        service.submitOrder(createOrderRequest("SELL", "100.00", "10"))
        // Taker (Buy)
        val buyResponse = service.submitOrder(createOrderRequest("BUY", "100.00", "10"))

        val bookView = service.getOrderBookView()
        assertTrue(bookView.Bids.isEmpty())
        assertTrue(bookView.Asks.isEmpty())

        val trades = service.getRecentTrades()
        assertEquals(1, trades.size)
        assertEquals(BigDecimal("100.00"), trades[0].price)
        assertEquals(BigDecimal("10"), trades[0].quantity)
        assertEquals(Side.BUY, trades[0].takerSide) // Buy order was the taker
        assertEquals("BTC/ZAR", trades[0].currencyPair)
        assertNotNull(buyResponse.orderId)
    }

    @Test
    fun `submit sell order, full match with one buy order`() {
        // Maker (Buy)
        service.submitOrder(createOrderRequest("BUY", "100.00", "10"))
        // Taker (Sell)
        val sellResponse = service.submitOrder(createOrderRequest("SELL", "100.00", "10"))

        val bookView = service.getOrderBookView()
        assertTrue(bookView.Bids.isEmpty())
        assertTrue(bookView.Asks.isEmpty())

        val trades = service.getRecentTrades()
        assertEquals(1, trades.size)
        assertEquals(BigDecimal("100.00"), trades[0].price)
        assertEquals(BigDecimal("10"), trades[0].quantity)
        assertEquals(Side.SELL, trades[0].takerSide)
        assertNotNull(sellResponse.orderId)
    }
    
    @Test
    fun `submit buy order, partial match, new order remaining`() {
        service.submitOrder(createOrderRequest("SELL", "100.00", "5")) // Maker
        val buyResponse = service.submitOrder(createOrderRequest("BUY", "100.00", "10")) // Taker

        val bookView = service.getOrderBookView()
        assertEquals(1, bookView.Bids.size)
        assertEquals(BigDecimal("5"), bookView.Bids[0].quantity) // Remaining part of buy order
        assertEquals(BigDecimal("100.00"), bookView.Bids[0].price)
        assertTrue(bookView.Asks.isEmpty())

        val trades = service.getRecentTrades()
        assertEquals(1, trades.size)
        assertEquals(BigDecimal("5"), trades[0].quantity)
        assertEquals(BigDecimal("100.00"), trades[0].price)
        assertEquals(Side.BUY, trades[0].takerSide)
        assertNotNull(buyResponse.orderId)
    }

    @Test
    fun `submit buy order, partial match, existing order remaining`() {
        service.submitOrder(createOrderRequest("SELL", "100.00", "10")) // Maker
        val buyResponse = service.submitOrder(createOrderRequest("BUY", "100.00", "5")) // Taker

        val bookView = service.getOrderBookView()
        assertTrue(bookView.Bids.isEmpty())
        assertEquals(1, bookView.Asks.size)
        assertEquals(BigDecimal("5"), bookView.Asks[0].quantity) // Remaining part of sell order
        assertEquals(BigDecimal("100.00"), bookView.Asks[0].price)


        val trades = service.getRecentTrades()
        assertEquals(1, trades.size)
        assertEquals(BigDecimal("5"), trades[0].quantity)
        assertEquals(BigDecimal("100.00"), trades[0].price)
        assertEquals(Side.BUY, trades[0].takerSide)
        assertNotNull(buyResponse.orderId)
    }

    @Test
    fun `submit buy order, matches multiple sell orders, new order fully filled`() {
        service.submitOrder(createOrderRequest("SELL", "100.00", "3"))
        service.submitOrder(createOrderRequest("SELL", "100.50", "3")) // Should match this first due to price for BUY
        service.submitOrder(createOrderRequest("SELL", "101.00", "4")) // Then this

        val buyResponse = service.submitOrder(createOrderRequest("BUY", "101.00", "10")) // Taker, price high enough to match all

        val bookView = service.getOrderBookView()
        assertTrue(bookView.Bids.isEmpty())
        assertTrue(bookView.Asks.isEmpty()) // All sell orders consumed

        val trades = service.getRecentTrades()
        assertEquals(3, trades.size) // One trade for each matched sell order

        // Trades are recorded in reverse chronological order (most recent first)
        // Match with 100.00 (qty 3)
        assertEquals(BigDecimal("3"), trades[2].quantity)
        assertEquals(BigDecimal("100.00"), trades[2].price)
        assertEquals(Side.BUY, trades[2].takerSide)

        // Match with 100.50 (qty 3)
        assertEquals(BigDecimal("3"), trades[1].quantity)
        assertEquals(BigDecimal("100.50"), trades[1].price)
        assertEquals(Side.BUY, trades[1].takerSide)
        
        // Match with 101.00 (qty 4)
        assertEquals(BigDecimal("4"), trades[0].quantity)
        assertEquals(BigDecimal("101.00"), trades[0].price)
        assertEquals(Side.BUY, trades[0].takerSide)
        assertNotNull(buyResponse.orderId)
    }
    
    @Test
    fun `submit buy order, matches multiple sell orders, new order partially filled`() {
        service.submitOrder(createOrderRequest("SELL", "100.00", "3"))
        service.submitOrder(createOrderRequest("SELL", "100.50", "3"))

        // Taker, price high enough to match all, quantity more than available
        val buyResponse = service.submitOrder(createOrderRequest("BUY", "100.50", "10")) 

        val bookView = service.getOrderBookView()
        assertEquals(1, bookView.Bids.size)
        assertEquals(BigDecimal("4"), bookView.Bids[0].quantity) // 10 - 3 - 3 = 4 remaining
        assertEquals(BigDecimal("100.50"), bookView.Bids[0].price)
        assertTrue(bookView.Asks.isEmpty())

        val trades = service.getRecentTrades()
        assertEquals(2, trades.size)
        // Trade 1 (match with 100.00, qty 3)
        assertEquals(BigDecimal("3"), trades[1].quantity)
        assertEquals(BigDecimal("100.00"), trades[1].price)
        // Trade 2 (match with 100.50, qty 3)
        assertEquals(BigDecimal("3"), trades[0].quantity)
        assertEquals(BigDecimal("100.50"), trades[0].price)
        assertNotNull(buyResponse.orderId)
    }

    @Test
    fun `order book view aggregates correctly`() {
        service.submitOrder(createOrderRequest("BUY", "100.00", "5"))
        service.submitOrder(createOrderRequest("BUY", "100.00", "10"))
        service.submitOrder(createOrderRequest("BUY", "99.00", "3"))

        service.submitOrder(createOrderRequest("SELL", "101.00", "7"))
        service.submitOrder(createOrderRequest("SELL", "101.00", "8"))
        service.submitOrder(createOrderRequest("SELL", "102.00", "4"))

        val bookView = service.getOrderBookView()

        assertEquals(2, bookView.Bids.size) // 100.00, 99.00
        assertEquals(BigDecimal("100.00"), bookView.Bids[0].price)
        assertEquals(BigDecimal("15"), bookView.Bids[0].quantity) // 5 + 10
        assertEquals(2, bookView.Bids[0].orderCount)
        assertEquals(BigDecimal("99.00"), bookView.Bids[1].price)
        assertEquals(BigDecimal("3"), bookView.Bids[1].quantity)
        assertEquals(1, bookView.Bids[1].orderCount)

        assertEquals(2, bookView.Asks.size) // 101.00, 102.00
        assertEquals(BigDecimal("101.00"), bookView.Asks[0].price)
        assertEquals(BigDecimal("15"), bookView.Asks[0].quantity) // 7 + 8
        assertEquals(2, bookView.Asks[0].orderCount)
        assertEquals(BigDecimal("102.00"), bookView.Asks[1].price)
        assertEquals(BigDecimal("4"), bookView.Asks[1].quantity)
        assertEquals(1, bookView.Asks[1].orderCount)
    }

    @Test
    fun `price and time priority for buy orders`() {
        service.submitOrder(createOrderRequest("BUY", "100", "1", "pair1")) // Order A
        Thread.sleep(10) // Ensure different timestamp
        service.submitOrder(createOrderRequest("BUY", "101", "1", "pair2")) // Order B (better price)
        Thread.sleep(10)
        service.submitOrder(createOrderRequest("BUY", "100", "1", "pair3")) // Order C (same price as A, but later)

        // Expected order in Bids (highest price first, then oldest): B, A, C
        val bids = service.getOrderBookView().Bids
        assertEquals(3, bids.size)
        assertEquals(BigDecimal("101"), bids[0].price) // Order B
        assertEquals(BigDecimal("100"), bids[1].price) // Order A
        assertEquals(BigDecimal("100"), bids[2].price) // Order C

        // Now match with a sell order that takes out B and A
        service.submitOrder(createOrderRequest("SELL", "100", "2"))
        
        val trades = service.getRecentTrades()
        assertEquals(2, trades.size)
        // Trade 1: Match with Order B (price 101, but trade at maker's price 101)
        // Sell order is at 100, so it should match B at 101. Trade price is maker's price (B's price).
        assertEquals(BigDecimal("101"), trades[1].price) 
        assertEquals(BigDecimal("1"), trades[1].quantity)
        // Trade 2: Match with Order A (price 100)
        assertEquals(BigDecimal("100"), trades[0].price)
        assertEquals(BigDecimal("1"), trades[0].quantity)

        // Remaining order in book should be C
        val remainingBids = service.getOrderBookView().Bids
        assertEquals(1, remainingBids.size)
        assertEquals(BigDecimal("100"), remainingBids[0].price)
        // Check if it's order C (e.g. by quantity if distinct, or need more specific ID in test if not)
        // This test relies on the order of insertion for timestamp when prices are equal.
        // Order C's original quantity was 1, so it should be 1.
        assertEquals(BigDecimal("1"), remainingBids[0].quantity) 
    }

    @Test
    fun `price and time priority for sell orders`() {
        service.submitOrder(createOrderRequest("SELL", "101", "1", "pair1")) // Order A
        Thread.sleep(10)
        service.submitOrder(createOrderRequest("SELL", "100", "1", "pair2")) // Order B (better price)
        Thread.sleep(10)
        service.submitOrder(createOrderRequest("SELL", "101", "1", "pair3")) // Order C (same price as A, but later)

        // Expected order in Asks (lowest price first, then oldest): B, A, C
        val asks = service.getOrderBookView().Asks
        assertEquals(3, asks.size)
        assertEquals(BigDecimal("100"), asks[0].price) // Order B
        assertEquals(BigDecimal("101"), asks[1].price) // Order A
        assertEquals(BigDecimal("101"), asks[2].price) // Order C

        // Match with a buy order that takes out B and A
        service.submitOrder(createOrderRequest("BUY", "101", "2"))

        val trades = service.getRecentTrades()
        assertEquals(2, trades.size)
        // Trade 1: Match with Order B (price 100)
        assertEquals(BigDecimal("100"), trades[1].price)
        assertEquals(BigDecimal("1"), trades[1].quantity)
        // Trade 2: Match with Order A (price 101)
        assertEquals(BigDecimal("101"), trades[0].price)
        assertEquals(BigDecimal("1"), trades[0].quantity)

        // Remaining order in book should be C
        val remainingAsks = service.getOrderBookView().Asks
        assertEquals(1, remainingAsks.size)
        assertEquals(BigDecimal("101"), remainingAsks[0].price)
        assertEquals(BigDecimal("1"), remainingAsks[0].quantity)
    }
    
    @Test
    fun `submit order with invalid side throws IllegalArgumentException`() {
        val request = createOrderRequest("INVALID_SIDE", "100.00", "10")
        assertThrows(IllegalArgumentException::class.java) {
            service.submitOrder(request)
        }
    }

    @Test
    fun `submit order with invalid quantity format throws IllegalArgumentException`() {
        val request = createOrderRequest("BUY", "100.00", "NOT_A_NUMBER")
        assertThrows(IllegalArgumentException::class.java) {
            service.submitOrder(request)
        }
    }
    
    @Test
    fun `submit order with zero quantity throws IllegalArgumentException`() {
        val request = createOrderRequest("BUY", "100.00", "0")
        assertThrows(IllegalArgumentException::class.java) {
            service.submitOrder(request)
        }
    }
    
    @Test
    fun `submit order with negative quantity throws IllegalArgumentException`() {
        val request = createOrderRequest("BUY", "100.00", "-1")
        assertThrows(IllegalArgumentException::class.java) {
            service.submitOrder(request)
        }
    }

    @Test
    fun `submit order with invalid price format throws IllegalArgumentException`() {
        val request = createOrderRequest("BUY", "NOT_A_PRICE", "10")
        assertThrows(IllegalArgumentException::class.java) {
            service.submitOrder(request)
        }
    }

    @Test
    fun `submit order with zero price throws IllegalArgumentException`() {
        val request = createOrderRequest("BUY", "0", "10")
        assertThrows(IllegalArgumentException::class.java) {
            service.submitOrder(request)
        }
    }
    
    @Test
    fun `submit order with negative price throws IllegalArgumentException`() {
        val request = createOrderRequest("BUY", "-100.00", "10")
        assertThrows(IllegalArgumentException::class.java) {
            service.submitOrder(request)
        }
    }
    
    @Test
    fun `trade history limited to 100 trades`() {
        for (i in 1..105) {
            // Create orders that will match to generate trades
            service.submitOrder(createOrderRequest("BUY", "100.00", "1", "BTC/ZAR"))
            service.submitOrder(createOrderRequest("SELL", "100.00", "1", "BTC/ZAR"))
        }
        val trades = service.getRecentTrades()
        assertEquals(100, trades.size)

        // Trades are added with an incrementing sequenceId.
        // The first trade added to history will have sequenceId = 1.
        // The last trade (most recent) will have sequenceId = 105.
        // Since history is capped at 100, oldest trades (1 to 5) are removed.
        // The most recent trade in history (at the head of the list) should be 105.
        assertEquals(105L, trades.first().sequenceId) 
        // The oldest trade in history (at the tail of the list) should be 105 - 100 + 1 = 6.
        assertEquals(6L, trades.last().sequenceId)
    }
}
