package net.corda.ledger.persistence.query.impl.parsing

class NotEquals : Keyword {
    override fun toString(): String {
        return "!="
    }

    override fun hashCode(): Int {
        return "!=".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class And : Keyword {
    override fun toString(): String {
        return "AND"
    }

    override fun hashCode(): Int {
        return "AND".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class Or : Keyword {
    override fun toString(): String {
        return "OR"
    }

    override fun hashCode(): Int {
        return "OR".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class IsNull : Keyword {
    override fun toString(): String {
        return "IS NULL"
    }

    override fun hashCode(): Int {
        return "IS NULL".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class IsNotNull : Keyword {
    override fun toString(): String {
        return "IS NOT NULL"
    }

    override fun hashCode(): Int {
        return "IS NOT NULL".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class GreaterThan : Keyword {
    override fun toString(): String {
        return ">"
    }

    override fun hashCode(): Int {
        return ">".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class GreaterThanEquals : Keyword {
    override fun toString(): String {
        return ">="
    }

    override fun hashCode(): Int {
        return ">=".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class LessThan : Keyword {
    override fun toString(): String {
        return "<"
    }

    override fun hashCode(): Int {
        return "<".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class LessThanEquals : Keyword {
    override fun toString(): String {
        return "<="
    }

    override fun hashCode(): Int {
        return "<=".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class JsonArrayOrObjectAsText : Keyword {
    override fun toString(): String {
        return "->>"
    }

    override fun hashCode(): Int {
        return "->>".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class As : Keyword {
    override fun toString(): String {
        return "AS"
    }

    override fun hashCode(): Int {
        return "AS".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class From : Keyword {
    override fun toString(): String {
        return "FROM"
    }

    override fun hashCode(): Int {
        return "FROM".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class Select : Keyword {
    override fun toString(): String {
        return "SELECT"
    }

    override fun hashCode(): Int {
        return "SELECT".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class Where : Keyword {
    override fun toString(): String {
        return "WHERE"
    }

    override fun hashCode(): Int {
        return "WHERE".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class Equals : Keyword {
    override fun toString(): String {
        return "="
    }

    override fun hashCode(): Int {
        return "=".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class In : Keyword {
    override fun toString(): String {
        return "IN"
    }

    override fun hashCode(): Int {
        return "IN".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class JsonKeyExists : Keyword {
    override fun toString(): String {
        return "?"
    }

    override fun hashCode(): Int {
        return "?".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

class Like : Keyword {
    override fun toString(): String {
        return "LIKE"
    }

    override fun hashCode(): Int {
        return "LIKE".hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }
}

fun operatorFactory(op: String): Keyword {
    return when (op.uppercase()) {
        "!=" -> NotEquals()
        ">" -> GreaterThan()
        ">=" -> GreaterThanEquals()
        "<" -> LessThan()
        "<=" -> LessThanEquals()
        "IS NULL" -> IsNull()
        "IS NOT NULL" -> IsNotNull()
        "->>" -> JsonArrayOrObjectAsText()
        "AS" -> As()
        "FROM" -> From()
        "SELECT" -> Select()
        "WHERE" -> Where()
        "OR" -> Or()
        "AND" -> And()
        "=" -> Equals()
        "IN" -> In()
        "?" -> JsonKeyExists()
        "LIKE" -> Like()
        else -> throw IllegalArgumentException("Unknown operator $op")
    }
}