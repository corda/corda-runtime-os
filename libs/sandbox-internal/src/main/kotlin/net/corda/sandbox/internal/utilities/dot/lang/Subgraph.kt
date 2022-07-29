package net.corda.sandbox.internal.utilities.dot.lang

open class Subgraph(open val id: String) : Statement, AttributeList, Statements {
    protected val statements = StatementList()
    override fun add(statement: Statement) = statement.apply { statements.add(statement) }
    override fun add(statements: List<Statement>) = statements.forEach(::add)

    override fun attrs(attributes: List<Pair<String, String>>) = attributes.forEach { add(IdStatement(it.first, it.second)) }

    override fun render(): String = "subgraph $id {\n${statements.render()}\n}"
}
