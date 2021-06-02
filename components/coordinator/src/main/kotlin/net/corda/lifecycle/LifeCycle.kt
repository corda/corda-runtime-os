package net.corda.lifecycle

interface LifeCycle: AutoCloseable {

    val isRunning: Boolean

    val timeout: Long

    fun start()

    fun stop(): Boolean

    //: AutoCloseable

    override fun close() {
        stop()
    }
}

