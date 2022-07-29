package net.corda.sandbox.internal.utilities.dot.lang

class Node(id: String, attrs: AttrList = AttrList()) : NodeId(id), Statement, AttributeList {
    private val attrList = AttrList()

    init {
        attrList.add(attrs)
    }

    constructor(id: String, attrs: List<Pair<String, String>>) : this(id, AttrList(attrs))

    override fun attrs(attributes: List<Pair<String, String>>) = attrs(AList(attributes))
    fun attrs(attributes: AList) = attrList.add(attributes)
    fun attrs(attributes: AttrList) = attrList.add(attributes)
    override fun render(): String = "\"$id\" ${attrList.render()}"
}
