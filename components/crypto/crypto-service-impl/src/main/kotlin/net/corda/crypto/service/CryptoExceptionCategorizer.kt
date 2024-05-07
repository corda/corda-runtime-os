package net.corda.crypto.service

interface CryptoExceptionCategorizer {
    fun categorize(exception: Exception): CryptoExceptionType
}

enum class CryptoExceptionType { FATAL, PLATFORM, TRANSIENT }
