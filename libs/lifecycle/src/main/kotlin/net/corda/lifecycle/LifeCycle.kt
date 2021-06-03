package net.corda.lifecycle

interface LifeCycle: AutoCloseable {

    val isRunning: Boolean

    fun start()

    fun stop()

    //: AutoCloseable

    override fun close() {
        stop()
    }
}

