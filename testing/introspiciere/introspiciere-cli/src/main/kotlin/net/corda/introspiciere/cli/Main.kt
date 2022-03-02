package net.corda.introspiciere.cli

import picocli.CommandLine
import java.io.Closeable
import java.io.Flushable
import java.io.InputStream
import java.io.OutputStream

fun main(vararg args: String) {
    internalMain(*args)
}

/**
 * Call the cli from tests.
 */
fun internalMain(
    vararg args: String,
    overrideStdin: InputStream = System.`in`,
    overrideStdout: OutputStream = System.out,
    overrideStderr: OutputStream = System.err,
) {
    stdin = overrideStdin
    stdout = overrideStdout
    stderr = overrideStderr
    CommandLine(Subcommands()).execute(*args)
}

/**
 * Use [stdin] instead of System.`in`.
 */
internal lateinit var stdin: InputStream
    private set

/**
 * Use [stdin] instead of System.`in`.
 */
internal lateinit var stdout: OutputStream
    private set

/**
 * Use [stdin] instead of System.`in`.
 */
internal lateinit var stderr: OutputStream
    private set

/**
 * Simple method to write to [stdout] a string.
 */
internal fun appendToStdout(value: CharSequence) {
    stdout.bufferedWriter().autoFlush { it.appendLine(value) }
}

/**
 * Similar behaviour as [use] with [Closeable] but flusing the resource instead of closing it.
 */
internal fun <T: Flushable, R> T.autoFlush(block: (T) -> R): R {
    Closeable{ this.flush() }.use {
        return block(this)
    }
}