package com.example.orderbook

data class OrderBookResponse(
    val Asks: List<OrderBookEntry>,
    val Bids: List<OrderBookEntry>
)
