package net.corda.sandbox.internal.utilities.dot.lang

/**
 * Top-most class.  Simple implementation of https://graphviz.org/doc/info/lang.html
 */
class Digraph(val id: String = "") : Render, AttributeList, Statements {
    companion object {
        val op = "->"
    }

    private val statements = StatementList()
    fun edgeRhs(nodeId: NodeId, rhs: List<EdgeRhs> = emptyList()) = EdgeRhs(op, nodeId, rhs)
    fun edgeRhs(subgraph: Subgraph, rhs: List<EdgeRhs> = emptyList()) = EdgeRhs(op, subgraph, rhs)
    override fun render(): String = "digraph $id {\n${statements.render()}\n}\n\n"

    override fun add(statement: Statement) = statement.apply { statements.add(statement) }
    override fun add(statements: List<Statement>) = statements.forEach(::add)

    fun edge(nodeA: NodeId, nodeB: NodeId) : Edge = Edge(nodeA, listOf(edgeRhs(nodeB))).apply { add(this) }
    fun edge(nodes: List<NodeId>) = Edge(nodes.first(), nodes.drop(1).map { EdgeRhs(op, it) })

    override fun attrs(attributes: List<Pair<String, String>>) = attributes.forEach { add(IdStatement(it.first, it.second)) }
}


//class Graph(val id: String, val statements: StatementList) {
//    fun edgeRhs(nodeLike: NodeLike, rhs: EdgeRhs? = null) = EdgeRhs("--", nodeLike, rhs)
//}
