package net.corda.ledger.persistence.query.parsing

interface Keyword : Token

@Suppress("unused")
enum class Associativity {
    Left,
    Right
}

/**
 * Operator precedence:
 * 1. <, <=, >, >=, AS, IS NULL, IS NOT NULL, IN, NOT IN, LIKE, NOT LIKE, ->, ->>, ?, ::
 * 1. =, !=
 * 1. AND
 * 1. OR
 */
interface Operator : Keyword {
    val associativity: Associativity
    val precedence: Int
}

interface UnaryKeyword : Keyword {
    val op: List<Token>

    fun create(ops: List<Token>): UnaryKeyword
}

interface TopLevelKeyword : UnaryKeyword

interface BinaryKeyword : Keyword {
    val op1: List<Token>
    val op2: List<Token>

    fun create(o1: List<Token>, o2: List<Token>): BinaryKeyword
}

class From(override val op: List<Token>) : TopLevelKeyword {
    constructor() : this(emptyList())

    override fun create(ops: List<Token>) = From(ops)
}

class Select(override val op: List<Token>) : TopLevelKeyword {
    constructor() : this(emptyList())

    override fun create(ops: List<Token>) = Select(ops)
}

class Where(override val op: List<Token>) : TopLevelKeyword {
    constructor() : this(emptyList())

    override fun create(ops: List<Token>) = Where(ops)
}

class Equals(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 2

    override fun create(o1: List<Token>, o2: List<Token>) = Equals(o1, o2)
}

class NotEquals(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 2

    override fun create(o1: List<Token>, o2: List<Token>) = NotEquals(o1, o2)
}

class And(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 3

    override fun create(o1: List<Token>, o2: List<Token>) = And(o1, o2)
}

class Or(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 4

    override fun create(o1: List<Token>, o2: List<Token>) = Or(o1, o2)
}

class IsNull(
    override val op: List<Token>
) : UnaryKeyword, Operator {
    constructor() : this(emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(ops: List<Token>) = IsNull(ops)
}

class IsNotNull(
    override val op: List<Token>
) : UnaryKeyword, Operator {
    constructor() : this(emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(ops: List<Token>) = IsNotNull(ops)
}

class GreaterThan(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = GreaterThan(o1, o2)
}

class GreaterThanEquals(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = GreaterThanEquals(o1, o2)
}

class LessThan(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = LessThan(o1, o2)
}

class LessThanEquals(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = LessThanEquals(o1, o2)
}

class JsonField(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = JsonField(o1, o2)
}

class JsonArrayOrObjectAsText(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = JsonArrayOrObjectAsText(o1, o2)
}

class As(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = As(o1, o2)
}

class In(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = In(o1, o2)
}

class NotIn(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = NotIn(o1, o2)
}

class JsonKeyExists(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = JsonKeyExists(o1, o2)
}

class Like(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = Like(o1, o2)
}

class NotLike(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = NotLike(o1, o2)
}

class JsonCast(
    override val op1: List<Token>,
    override val op2: List<Token>
) : BinaryKeyword, Operator {
    constructor() : this(emptyList(), emptyList())

    override val associativity: Associativity
        get() = Associativity.Left

    override val precedence: Int
        get() = 1

    override fun create(o1: List<Token>, o2: List<Token>) = JsonCast(o1, o2)
}
