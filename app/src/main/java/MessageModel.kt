package com.example.jonim

data class MessageModel(
    val text: String,
    val fromSelf: Boolean,
    val senderName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)