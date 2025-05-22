package com.example.orderbook

import java.math.BigDecimal
import java.util.UUID

data class Trade(
    val id: String = UUID.randomUUID().toString(),
    val currencyPair: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val tradedAt: Long = System.currentTimeMillis(),
    val takerSide: Side,
    val sequenceId: Long
)
