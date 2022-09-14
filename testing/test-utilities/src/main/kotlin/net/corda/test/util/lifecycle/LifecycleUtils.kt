package net.corda.test.util.lifecycle

import net.corda.lifecycle.Lifecycle

/**
 * Similar to a `.use` block for `AutoCloseable` but calls `stop` on the
 * `Lifecycle` at the end.
 */
fun <T : Lifecycle?, R> T.usingLifecycle(block: (T) -> R): R {
    return block.invoke(this).also {
        this?.stop()
    }
}
