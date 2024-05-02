package net.corda.orm

interface PersistenceExceptionCategorizer {

    fun categorize(exception: Exception): PersistenceExceptionType
}

enum class PersistenceExceptionType { FATAL, DATA_RELATED, TRANSIENT, UNCATEGORIZED }