package com.example.orderbook

import java.math.BigDecimal
import java.util.PriorityQueue
import java.util.LinkedList // For trade history

class OrderBookService {

    // Buy orders: Max-heap (highest price first). If prices are equal, older order gets priority.
    private val buyOrders = PriorityQueue<Order>(compareByDescending<Order> { it.price }.thenBy { it.timestamp })

    // Sell orders: Min-heap (lowest price first). If prices are equal, older order gets priority.
    private val sellOrders = PriorityQueue<Order>(compareBy<Order> { it.price }.thenBy { it.timestamp })

    private val tradeHistory = LinkedList<Trade>() // Stores recent trades
    private var nextSequenceId = 1L

    fun submitOrder(orderRequest: LimitOrderRequest): LimitOrderResponse {
        // Validate and convert request strings to appropriate types
        val side = try {
            Side.valueOf(orderRequest.side.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid order side: ${orderRequest.side}")
        }
        val quantity = orderRequest.quantity.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Invalid quantity format: ${orderRequest.quantity}")
        val price = orderRequest.price.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Invalid price format: ${orderRequest.price}")

        if (quantity <= BigDecimal.ZERO) throw IllegalArgumentException("Quantity must be positive")
        if (price <= BigDecimal.ZERO) throw IllegalArgumentException("Price must be positive")

        val order = Order(
            side = side,
            quantity = quantity,
            price = price,
            currencyPair = orderRequest.pair // Assuming pair is validated or fixed if necessary
            // orderId, timestamp, remainingQuantity are set by Order data class defaults
        )

        matchOrders(order)

        if (order.remainingQuantity > BigDecimal.ZERO) {
            if (order.side == Side.BUY) {
                buyOrders.add(order)
            } else {
                sellOrders.add(order)
            }
        }
        return LimitOrderResponse(order.orderId)
    }

    private fun matchOrders(newOrder: Order) {
        val bookToMatch = if (newOrder.side == Side.BUY) sellOrders else buyOrders

        while (bookToMatch.isNotEmpty() && newOrder.remainingQuantity > BigDecimal.ZERO) {
            val topOfBook = bookToMatch.peek() // Look at the best priced order

            val canMatch = if (newOrder.side == Side.BUY) {
                newOrder.price >= topOfBook.price // Buy order price is high enough
            } else {
                newOrder.price <= topOfBook.price // Sell order price is low enough
            }

            if (!canMatch) {
                break // No match possible at the current price levels
            }

            val tradeQuantity = newOrder.remainingQuantity.min(topOfBook.remainingQuantity)
            val tradePrice = topOfBook.price // Trades occur at the price of the order already in the book (maker's price)

            // Create trade record
            val trade = Trade(
                currencyPair = newOrder.currencyPair,
                price = tradePrice,
                quantity = tradeQuantity,
                takerSide = newOrder.side,
                sequenceId = nextSequenceId++
                // id and tradedAt are set by Trade data class defaults
            )
            tradeHistory.addFirst(trade) // Add to the beginning for recent trades
            if (tradeHistory.size > 100) { // Keep only last 100 trades
                tradeHistory.removeLast()
            }

            // Update remaining quantities
            newOrder.remainingQuantity -= tradeQuantity
            topOfBook.remainingQuantity -= tradeQuantity

            // If topOfBook order is fully filled, remove it
            if (topOfBook.remainingQuantity <= BigDecimal.ZERO) {
                bookToMatch.poll() // Remove the matched order
            }

            if (newOrder.remainingQuantity <= BigDecimal.ZERO) {
                break // New order is fully filled
            }
        }
    }

    fun getOrderBookView(): OrderBookResponse {
        val asks = mutableMapOf<BigDecimal, Pair<BigDecimal, Int>>() // Price -> (TotalQuantity, OrderCount)
        // Iterate over a copy to avoid concurrent modification issues if service were multi-threaded
        // For single-threaded Vert.x, direct iteration is fine but copy is safer for future.
        ArrayList(sellOrders).forEach { order ->
            val current = asks.getOrDefault(order.price, Pair(BigDecimal.ZERO, 0))
            asks[order.price] = Pair(current.first + order.remainingQuantity, current.second + 1)
        }

        val bids = mutableMapOf<BigDecimal, Pair<BigDecimal, Int>>()
        ArrayList(buyOrders).forEach { order ->
            val current = bids.getOrDefault(order.price, Pair(BigDecimal.ZERO, 0))
            bids[order.price] = Pair(current.first + order.remainingQuantity, current.second + 1)
        }

        // Convert to list of OrderBookEntry and sort: Asks ascending, Bids descending by price
        val askEntries = asks.entries.map { OrderBookEntry(it.key, it.value.first, it.value.second) }.sortedBy { it.price }
        val bidEntries = bids.entries.map { OrderBookEntry(it.key, it.value.first, it.value.second) }.sortedByDescending { it.price }

        return OrderBookResponse(Asks = askEntries, Bids = bidEntries)
    }

    fun getRecentTrades(): List<Trade> {
        return tradeHistory.toList() // Returns a copy
    }
}
