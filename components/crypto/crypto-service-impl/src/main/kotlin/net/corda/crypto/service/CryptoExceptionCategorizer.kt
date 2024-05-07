package net.corda.crypto.service

// This interface duplicates some of the logic of the [PersistenceExceptionCategorizer]
// Their definitions should be coalesced in some way to reduce this duplication
interface CryptoExceptionCategorizer {
    fun categorize(exception: Exception): CryptoExceptionType
}

enum class CryptoExceptionType { FATAL, PLATFORM, TRANSIENT }
