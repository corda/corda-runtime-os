package net.corda.ledger.persistence.query.parsing

interface Reference : Token {
    val ref: String
}

data class PathReference(override val ref: String) : Reference

data class PathReferenceWithSpaces(override val ref: String) : Reference

data class Number(override val ref: String) : Reference

data class Parameter(override val ref: String) : Reference

data class SqlType(override val ref: String) : Reference
