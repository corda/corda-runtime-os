package net.corda.persistence.common

internal interface PersistenceExceptionCategorizer {

    fun categorize(exception: Exception): PersistenceExceptionType
}

internal enum class PersistenceExceptionType { FATAL, PLATFORM, TRANSIENT }