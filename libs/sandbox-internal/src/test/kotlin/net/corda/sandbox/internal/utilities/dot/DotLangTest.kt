package net.corda.sandbox.internal.utilities.dot

import net.corda.sandbox.internal.utilities.dot.lang.AList
import net.corda.sandbox.internal.utilities.dot.lang.AttrList
import net.corda.sandbox.internal.utilities.dot.lang.AttrStatement
import net.corda.sandbox.internal.utilities.dot.lang.ClusterSubgraph
import net.corda.sandbox.internal.utilities.dot.lang.Digraph
import net.corda.sandbox.internal.utilities.dot.lang.Edge
import net.corda.sandbox.internal.utilities.dot.lang.EdgeRhs
import net.corda.sandbox.internal.utilities.dot.lang.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DotLangTest {
    @Test
    fun `test AList`() {
        val a = AList()
        assertThat(a.render()).isEqualTo("")
        a.add(listOf("color" to "yellow"))
        assertThat(a.render()).isEqualTo("color=\"yellow\"")
        a.add(listOf("font" to "Times"))
        assertThat(a.render()).isEqualTo("color=\"yellow\";font=\"Times\"")
    }

    @Test
    fun `test AttrList`() {
        val a = AttrList()
        assertThat(a.render()).isEqualTo("")
        a.add(listOf("color" to "yellow"))
        assertThat(a.render()).isEqualTo("[color=\"yellow\"]")
        a.add(listOf("shape" to "box", "font" to "Times"))
        assertThat(a.render()).isEqualTo("[color=\"yellow\"][shape=\"box\";font=\"Times\"]")
    }

    @Test
    fun `test node`() {
        val n = Node("a", AttrList().apply { add(listOf("color" to "yellow"))})
        assertThat(n.render()).isEqualTo("\"a\" [color=\"yellow\"]")
    }

    @Test
    fun `test edge`() {
        val a = Node("a", AttrList().apply { add(listOf("color" to "yellow"))})
        val b = Node("b", AttrList().apply { add(listOf("color" to "yellow"))})
        val c = Node("c", AttrList().apply { add(listOf("color" to "yellow"))})
        val eb = EdgeRhs("->", b)
        val ec = EdgeRhs("->", c)
        assertThat(eb.render()).isEqualTo(" -> \"b\"")
        assertThat(Edge(a, listOf(eb)).render()).isEqualTo("\"a\" -> \"b\"")
        assertThat(Edge(a, listOf(eb,ec)).render()).isEqualTo("\"a\" -> \"b\" -> \"c\"")

        val g = Digraph()
        assertThat(g.edge(listOf(a,b,c)).render()).isEqualTo("\"a\" -> \"b\" -> \"c\"")
    }

    @Test
    fun `digraph`() {
        val g = Digraph("test")
        g.attrs(listOf("fontsize" to "11", "fontname" to "sans-serif"))
        val attrs = AttrList().apply { add(listOf("fontsize" to "10", "color" to "yellow"))}
        g.add(AttrStatement(AttrStatement.Type.node, attrs))

        val c = ClusterSubgraph("aaa")
        val a = Node("a", attrs)
        val b = Node("b", attrs)
        g.edge(a, b)

        c.add(listOf(a,b))
        g.add(c)
        val actual = g.render()
        //digraph test {
        //fontsize="11";
        //fontname="sans-serif";
        //node [fontsize="10";color="yellow"];
        //"a" -> "b";
        //cluster_aaa {
        //"a" [fontsize="10";color="yellow"];
        //"b" [fontsize="10";color="yellow"]
        //}
        //}
        assertThat(actual).contains("digraph")
        assertThat(actual).contains("cluster_aaa")
        assertThat(actual).contains("\"a\" -> \"b\"")
    }
}
