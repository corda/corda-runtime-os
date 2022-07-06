package net.corda.sandbox.internal.utilities.dot.lang

class AttrStatement(val type: Type, val attrList: AttrList) : Statement, AttributeList {
    constructor(type: Type, attributes:List<Pair<String,String>>) : this(type, AttrList(attributes))

    enum class Type { graph, node, edge }

    override fun render(): String = "$type ${attrList.render()}"

    override fun attrs(attributes: List<Pair<String, String>>) = attrList.add(attributes)
}
