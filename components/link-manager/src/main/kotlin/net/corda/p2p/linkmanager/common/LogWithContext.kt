package net.corda.p2p.linkmanager.common

object LogWithContext {
    private val mySource = ThreadLocal<Exception>()

    fun create(): Exception {
        return Exception("QQQ", mySource.get())
    }
    fun set(exception: Exception) {
        mySource.set(exception)
    }

    fun reset() {
        mySource.set(null)
    }
}
