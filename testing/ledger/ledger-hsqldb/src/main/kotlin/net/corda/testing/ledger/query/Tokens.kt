package net.corda.testing.ledger.query

import net.corda.ledger.persistence.query.parsing.HasLeftParenthesis
import net.corda.ledger.persistence.query.parsing.Keyword

class HsqldbJsonArrayOrObjectAsText : Keyword, HasLeftParenthesis {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is HsqldbJsonArrayOrObjectAsText)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class HsqldbJsonField : Keyword, HasLeftParenthesis {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is HsqldbJsonField)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class HsqldbJsonKeyExists : Keyword, HasLeftParenthesis {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is HsqldbJsonKeyExists)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class HsqldbCast : Keyword, HasLeftParenthesis {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is HsqldbCast)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}
