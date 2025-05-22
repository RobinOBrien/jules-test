package com.example.orderbook

// Ensure BigDecimal is used for quantity and price if direct deserialization is planned,
// or handle String conversion carefully in the service/handler.
// Using String for now as per VALR reference, conversion/validation will be needed.
data class LimitOrderRequest(
    val side: String, // "BUY" or "SELL"
    val quantity: String,
    val price: String,
    val pair: String = "BTC/ZAR" // Default, can be overridden by request
)
