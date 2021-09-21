package net.corda.configuration.read.file.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.read.ConfigReadService
import java.util.concurrent.ConcurrentHashMap

class FileConfigurationHandlerStorage {

    private val handlers: MutableMap<CallbackHandle, Unit> = ConcurrentHashMap()

    private val subscription: ConfigReadService? = null

    private class CallbackHandle(
        private val callback: ConfigurationHandler,
        private val storage: FileConfigurationHandlerStorage
    ) : AutoCloseable {

        private var handle: AutoCloseable? = null

        fun subscribe(subscription: ConfigReadService) {
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

    fun add(callback: ConfigurationHandler): AutoCloseable {
        val sub = subscription
        val handle = CallbackHandle(callback, this)
        handlers[handle] = Unit
        if (sub != null) {
            handle.subscribe(sub)
        }
        return handle
    }

    fun addSubscription(subscription: ConfigReadService) {
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