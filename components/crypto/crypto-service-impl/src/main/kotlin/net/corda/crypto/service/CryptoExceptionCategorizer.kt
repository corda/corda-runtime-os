package net.corda.crypto.service

fun interface CryptoExceptionCategorizer {
    fun categorize(exception: Exception): CryptoExceptionType
}

enum class CryptoExceptionType { FATAL, PLATFORM, TRANSIENT }
