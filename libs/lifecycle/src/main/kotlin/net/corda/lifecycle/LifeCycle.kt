package net.corda.lifecycle

interface LifeCycle: AutoCloseable {

    val isRunning: Boolean

    val timeout: Long

    fun start()

    fun stop()

    //: AutoCloseable

    override fun close() {
        stop()
    }
}

