package net.corda.sandbox.internal.utilities.dot.lang

class ClusterSubgraph(override val id: String) : Subgraph(id) {
    override fun render(): String = "cluster_$id {\n${statements.render()}\n}"
}
