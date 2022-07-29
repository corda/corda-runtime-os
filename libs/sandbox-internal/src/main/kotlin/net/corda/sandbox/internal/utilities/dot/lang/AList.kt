package net.corda.sandbox.internal.utilities.dot.lang

/**
 * https://graphviz.org/doc/info/lang.html
 *
 * ID '=' ID [ (';' | ',') ] [ a_list ]
 *
 * ID '=' ID == [IdStatement]
 */
class AList(ids: List<Pair<String, String>> = emptyList()) : Render {
    private val statements = mutableListOf<IdStatement>()

    init {
        add(ids)
    }

    fun add(ids: List<Pair<String, String>>) = ids.forEach { statements.add(IdStatement(it.first, it.second)) }
    fun add(stmt: IdStatement) = statements.add(stmt)
    fun addIds(stmts: List<IdStatement>) = stmts.forEach(::add)
    override fun render(): String =
        if (statements.isEmpty()) "" else statements.joinToString(";") { it.render() }

}
