package net.corda.httprpc.server.utils

fun <T: Any?> executeWithThreadContextClassLoader(classloader: ClassLoader, fn: () -> T): T {
    val threadClassLoader = Thread.currentThread().contextClassLoader
    try {
        Thread.currentThread().contextClassLoader = classloader
        return fn()
    } finally {
        Thread.currentThread().contextClassLoader = threadClassLoader
    }

}
