package com.example.orderbook

import java.math.BigDecimal

data class OrderBookEntry(
    val price: BigDecimal,
    val quantity: BigDecimal,
    val orderCount: Int
)
