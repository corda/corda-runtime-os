package net.corda.ledger.persistence.query.parsing

interface Keyword : Token

class NotEquals : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is NotEquals)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class And : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is And)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class Or : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is Or)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class IsNull : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is IsNull)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class IsNotNull : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is IsNotNull)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class GreaterThan : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is GreaterThan)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class GreaterThanEquals : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is GreaterThanEquals)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class LessThan : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is LessThan)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class LessThanEquals : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is LessThanEquals)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class JsonField : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is JsonField)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class JsonArrayOrObjectAsText : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is JsonArrayOrObjectAsText)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class As : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is As)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class From : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is From)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class Select : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is Select)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class Where : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is Where)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class Equals : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is Equals)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class In : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is In)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class JsonKeyExists : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is JsonKeyExists)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

class Like : Keyword {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is Like)
    }

    override fun hashCode(): Int {
        return this::class.java.hashCode()
    }
}

data class JsonCast(val value: String) : Keyword

data class SqlType(val type: String) : Keyword
