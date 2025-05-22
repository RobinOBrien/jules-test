package com.example.orderbook

import java.math.BigDecimal
import java.util.UUID

data class Order(
    val orderId: String = UUID.randomUUID().toString(),
    val side: Side,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val timestamp: Long = System.currentTimeMillis(),
    val currencyPair: String = "BTC/ZAR", // Default, can be set if needed
    var remainingQuantity: BigDecimal = quantity
)
