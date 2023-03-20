package net.corda.ledger.persistence.query.impl.parsing

interface Token

interface Reference : Token {
    val ref: String
}

interface Keyword : Token

data class PathReference(override val ref: String) : Reference

data class PathReferenceWithSpaces(override val ref: String) : Reference

data class Number(override val ref: String) : Reference

data class JsonCast(val value: String) : Keyword

data class Parameter(override val ref: String) : Reference

class LeftParentheses : Token {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

class RightParentheses : Token {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

class ParameterEnd : Token {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}