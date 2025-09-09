package com.example.jonim

public final data class Message(
    public final val text: String,
    public final val fromSelf: Boolean,
    public final val timestamp: Long = System.currentTimeMillis()
)