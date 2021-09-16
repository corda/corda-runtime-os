package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.read.ConfigReader
import java.util.concurrent.ConcurrentHashMap

class ConfigurationHandlerStorage {

    private val handlers: MutableMap<CallbackHandle, Unit> = ConcurrentHashMap()

    private val subscription: ConfigReader? = null

    private class CallbackHandle(
        private val callback: ConfigurationHandler,
        private val storage: ConfigurationHandlerStorage
    ) : AutoCloseable {

        private var handle: AutoCloseable? = null

        fun subscribe(subscription: ConfigReader) {
            handle?.close()
            handle = subscription.registerCallback(callback::onNewConfiguration)
        }

        fun unregister() {
            handle?.close()
            handle = null
        }

        override fun close() {
            storage.remove(this)
            handle?.close()
            handle = null
        }
    }

    private fun remove(handle: CallbackHandle) {
        handlers.remove(handle)
    }

    fun add(callback: ConfigurationHandler) : AutoCloseable {
        val sub = subscription
        val handle = CallbackHandle(callback, this)
        handlers[handle] = Unit
        if (sub != null) {
            handle.subscribe(sub)
        }
        return handle
    }

    fun addSubscription(subscription: ConfigReader) {
        handlers.keys.forEach {
            it.subscribe(subscription)
        }
    }

    fun removeSubscription() {
        handlers.keys.forEach {
            it.unregister()
        }
    }
}