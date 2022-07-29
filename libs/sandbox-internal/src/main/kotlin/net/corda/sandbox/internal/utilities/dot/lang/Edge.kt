package net.corda.sandbox.internal.utilities.dot.lang

class Edge : Statement, AttributeList {
    private val nodeId: NodeId?
    private val subgraph: Subgraph?
    private val rhs: List<EdgeRhs>
    private val attrList: AttrList

    constructor(nodeId: NodeId, rhs: List<EdgeRhs>, attrList: AttrList = AttrList()) {
        this.nodeId = nodeId
        this.subgraph = null
        this.rhs = rhs
        this.attrList = attrList
    }

    constructor(subgraph: Subgraph, rhs: List<EdgeRhs>, attrList: AttrList = AttrList()) {
        this.nodeId = null
        this.subgraph = subgraph
        this.rhs = rhs
        this.attrList = attrList
    }

    override fun render(): String {
        val other = rhs.joinToString("") { it.render() }

        return when {
            nodeId != null -> "\"${nodeId.id}\"$other${attrList.render()}"
            subgraph != null -> "${subgraph.render()}$other${attrList.render()}"
            else -> ""
        }
    }

    override fun attrs(attributes: List<Pair<String, String>>) = attrList.add(attributes)
}
