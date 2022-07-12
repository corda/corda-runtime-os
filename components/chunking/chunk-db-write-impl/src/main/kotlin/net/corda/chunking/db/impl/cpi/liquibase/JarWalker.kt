package net.corda.chunking.db.impl.cpi.liquibase

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.jar.JarInputStream


/**
 * Simple class to traverse a jar input stream, and call 'onEntry' for each entry.
 */
object JarWalker {
    /**
     * Don't descend into lots of nested jars - we own the CPK format,
     * we expect a jar immediately inside the CPK at the 'root' level of the archive.
     *
     * Allow for a `cpi`, that contains a `cpb`, that contain the "final" `jar`
     */
    private const val maxDepth = 2

    /**
     * Walk a jar-like archive and call the [onEntry] callback.
     *
     * We call this method on a CPK, and then again, on the `jar` inside it.
     */
    fun walk(inputStream: InputStream, onEntry: (String, InputStream) -> Unit) = walk(0, inputStream, onEntry)

    @Suppress("NestedBlockDepth")
    private fun walk(depth: Int, inputStream: InputStream, onEntry: (String, InputStream) -> Unit) {
        // We don't own the input stream, so we don't close it.
        // Instead, read bytes into a buffer that we *do own*.
        // As long as no-one loads a multi-Gb jar we're fine (this is the same
        // jar loading code as the cpk/cpi loaders so that's doomed too).
        val buffer = inputStream.readAllBytes()

        // We "own" the CPK format, the actual "jar" is at the top level.
        if (depth > maxDepth) return

        ByteArrayInputStream(buffer).use {
            JarInputStream(it).use { jarInputStream ->
                while (true) {
                    val entry = jarInputStream.nextJarEntry ?: break

                    // We *might* want to additionally filter on the jar here as well
                    // to ensure it's a Cordapp one.
                    if (entry.name.lowercase().endsWith(".jar") || entry.name.lowercase().endsWith(".cpk")) {
                        walk(depth + 1, UncloseableInputStream(jarInputStream), onEntry)
                    } else {
                        onEntry(entry.name, UncloseableInputStream(jarInputStream))
                    }
                }
            }
        }
    }

    /** We don't want any method to close the jar input stream other than "us". */
    internal class UncloseableInputStream(source: InputStream) : FilterInputStream(source) {
        override fun close() {}
    }
}
