package net.corda.sandbox.internal.utilities.dot.lang

class EdgeRhs : Render {
    private val op: String
    private val nodeId: NodeId?
    private val subgraph: Subgraph?
    private val rhs: List<EdgeRhs>

    constructor(op: String, nodeId: NodeId, rhs: EdgeRhs) {
        this.op = op
        this.nodeId = nodeId
        this.subgraph = null
        this.rhs = listOf(rhs)
    }
    constructor(op: String, nodeId: NodeId, rhs: List<EdgeRhs> = emptyList()) {
        this.op = op
        this.nodeId = nodeId
        this.subgraph = null
        this.rhs = rhs
    }

    constructor(op: String, subgraph: Subgraph, rhs: List<EdgeRhs> = emptyList()) {
        this.op = op
        this.nodeId = null
        this.subgraph = subgraph
        this.rhs = rhs
    }

    override fun render(): String {
        val other = rhs.joinToString("") { it.render() }
        return when {
            nodeId != null -> " $op \"${nodeId.id}\"$other"
            subgraph != null -> " $op ${subgraph.render()}$other"
            else -> ""
        }
    }
}
