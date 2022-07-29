package net.corda.sandbox.internal.utilities.dot.lang

class AttrList(attributes:List<Pair<String,String>> = emptyList()) : Render {
    private val s = mutableListOf<String>()
    init {
        add(attributes)
    }
    fun add(a: AList) {
        val ss = a.render()
        if (ss.isNotEmpty()) {
            s.add("[$ss]")
        }
    }
    fun add(a: AttrList) = s.add("${a.render()}")
    fun addIds(attributes: List<IdStatement>) = add(AList().apply { addIds(attributes) })
    fun add(attributes: List<Pair<String, String>>) = add(AList(attributes))
    override fun render(): String = s.joinToString("")
}
