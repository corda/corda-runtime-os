package net.corda.utilities.classload

/**
 * Switches current thread's `contextClassLoader` to the class loader supplied and executes a code block.
 * Once code block is finished - restores thread's `contextClassLoader` to the original value.
 */
fun <T : Any?> executeWithThreadContextClassLoader(classloader: ClassLoader, fn: () -> T): T {
    val threadClassLoader = Thread.currentThread().contextClassLoader
    try {
        Thread.currentThread().contextClassLoader = classloader
        return fn()
    } finally {
        Thread.currentThread().contextClassLoader = threadClassLoader
    }
}
