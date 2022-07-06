package net.corda.sandbox.internal.utilities.dot

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.Sandbox
import net.corda.sandbox.internal.sandbox.SandboxImpl
import net.corda.sandbox.internal.utilities.dot.lang.AttrStatement
import net.corda.sandbox.internal.utilities.dot.lang.Digraph
import net.corda.sandbox.internal.utilities.dot.lang.Node
import net.corda.sandbox.internal.utilities.dot.lang.NodeId
import net.corda.sandbox.internal.utilities.dot.lang.Subgraph
import org.osgi.framework.Bundle
import java.io.OutputStream
import java.util.UUID

internal class DotSandboxVisitor(private val service: SandboxContextService, private val outputStream: OutputStream) :
    SandboxVisitor {
    class DecoratedBundle(val bundle: Bundle, val isVisible: Boolean)

    private val bundlesBySandboxId = mutableMapOf<UUID, MutableList<DecoratedBundle>>()
    private val sandboxIdByBundle = mutableMapOf<Bundle, UUID>()
    private val cpkMetadataById = mutableMapOf<UUID, CpkMetadata>()

    override fun visit(sandbox: Sandbox) {
        when (sandbox) {
            is CpkSandboxImpl -> addSandboxInfo(sandbox.id, sandbox.cpkMetadata)
            is SandboxImpl -> { /* unused */
            }
            else -> println("Unknown sandbox type")
        }
    }

    override fun visit(bundle: SandboxVisitor.PrivateBundle) = addBundle(bundle.thisSandboxId, bundle.bundle, false)

    override fun visit(bundle: SandboxVisitor.PublicBundle) = addBundle(bundle.thisSandboxId, bundle.bundle, true)

    private fun addBundle(sandboxId: UUID, bundle: Bundle, isVisible: Boolean) {
        bundlesBySandboxId.computeIfAbsent(sandboxId) { mutableListOf() }.add(DecoratedBundle(bundle, isVisible))
        sandboxIdByBundle[bundle] = sandboxId
    }

    private fun addSandboxInfo(id: UUID, cpkMetadata: CpkMetadata) {
        cpkMetadataById[id] = cpkMetadata
    }

    override fun complete() {
        val g = Digraph()
        val commonAttributes = listOf("fontname" to "sans-serif", "fontsize" to "9")
        g.attrs(commonAttributes)
        g.add(AttrStatement(AttrStatement.Type.node, commonAttributes))
        g.add(AttrStatement(AttrStatement.Type.edge, commonAttributes))

        generateBundleNodes(g)
        generateBundleEdges(g)

        val writer = outputStream.bufferedWriter()
        writer.write(g.render())
        writer.flush()
    }

    /**
     * we can have *lots* of edges, up to N*N-1 bundles if we render all of them
     * but we will skip edges internal to a sandbox
     */
    private fun generateBundleEdges(g: Digraph) {
        val allBundles = bundlesBySandboxId.flatMap { it.value }.toSet()
        allBundles.forEach { outer ->
            allBundles.forEach { inner ->
                // Don't render edges in same sandbox, it's a mess.
                if (outer != inner && !inSameSandbox(inner, outer)) {
                    if (service.hasVisibility(outer.bundle, inner.bundle)) {
                        g.edge(NodeId(nodeId(outer.bundle)), NodeId(nodeId(inner.bundle)))
                    }
                }
            }
        }
    }

    private fun inSameSandbox(inner: DecoratedBundle, outer: DecoratedBundle) =
        sandboxIdByBundle[inner.bundle]!! == sandboxIdByBundle[outer.bundle]

    private fun nodeId(bundle: Bundle) = "bundle_${bundle.bundleId}".replace("-", "_")
    private fun cpkName(cpkMetadata: CpkMetadata) =
        "CPK name=${cpkMetadata.cpkId.name} version=${cpkMetadata.cpkId.version}"

    private fun generateBundleNodes(digraph: Digraph) {
        val allBundles = bundlesBySandboxId.flatMap { it.value }.toSet()
        bundlesBySandboxId.keys.forEach { uuid ->
            val attrs = listOf("color" to "orange", "style" to "filled", "label" to "${subgraphLabel(uuid)}")
            val sandboxSubgraph = Subgraph("cluster_$uuid".replace("-", "_")).apply { attrs(attrs) }
            digraph.add(sandboxSubgraph)
            addNodesForSandbox(sandboxSubgraph, uuid)
        }

        // Write out the actual node "instances"
        allBundles.forEach {
            val colour = if (it.isVisible) "green" else "red"
            digraph.add(
                Node(
                    nodeId(it.bundle),
                    listOf("shape" to "box", "label" to it.bundle.symbolicName, "style" to "filled", "color" to colour)
                )
            )
        }
    }

    private fun subgraphLabel(uuid: UUID): String = if (cpkMetadataById.keys.contains(uuid)) {
        "$uuid\n(${cpkName(cpkMetadataById[uuid]!!)})"
    } else {
        "$uuid"
    }

    private fun addNodesForSandbox(sandboxSubgraph: Subgraph, uuid: UUID) {
        val (publicBundles, privateBundles) = bundlesBySandboxId[uuid]!!.partition { it.isVisible }

        val redAttrs = listOf("color" to "red3", "style" to "filled", "label" to "")
        val greenAttrs = listOf("color" to "green3", "style" to "filled", "label" to "")

        val s = Subgraph("cluster-public-$uuid".replace("-", "_")).apply { attrs(greenAttrs) }
        sandboxSubgraph.add(s)
        publicBundles.forEach { s.add(Node(nodeId(it.bundle))) }

        val s2 = Subgraph("cluster-private-$uuid".replace("-", "_")).apply { attrs(redAttrs) }
        sandboxSubgraph.add(s2)
        privateBundles.forEach { s2.add(Node(nodeId(it.bundle))) }
    }
}
