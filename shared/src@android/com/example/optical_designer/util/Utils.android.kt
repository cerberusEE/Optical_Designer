package com.example.optical_designer.util

import java.util.UUID

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun generateUuid(): String = UUID.randomUUID().toString()
