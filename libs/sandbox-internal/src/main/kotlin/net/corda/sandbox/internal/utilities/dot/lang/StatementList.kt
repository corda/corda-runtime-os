package net.corda.sandbox.internal.utilities.dot.lang

class StatementList() : Render {
    private val statements = mutableListOf<Statement>()
    fun add(statement: Statement) = statements.add(statement)

    constructor(statements: List<Statement>) : this() {
        statements.forEach(::add)
    }

    override fun render(): String = statements.joinToString(";\n") { it.render() }
}
