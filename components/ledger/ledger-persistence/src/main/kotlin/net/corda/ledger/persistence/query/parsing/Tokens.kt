package net.corda.ledger.persistence.query.parsing

interface Token

interface HasLeftParenthesis

class LeftParentheses : Token, HasLeftParenthesis {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is LeftParentheses)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class RightParentheses : Token {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is RightParentheses)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class ParameterEnd : Token {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is ParameterEnd)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}