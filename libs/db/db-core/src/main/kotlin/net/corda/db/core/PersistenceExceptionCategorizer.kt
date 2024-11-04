package net.corda.db.core

interface PersistenceExceptionCategorizer {

    fun categorize(exception: Exception): PersistenceExceptionType
}

enum class PersistenceExceptionType { FATAL, DATA_RELATED, TRANSIENT, UNCATEGORIZED }