package net.corda.httprpc.test.utils

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.Hashtable

internal class TestURLStreamHandlerFactory(content: Map<String, String>) : URLStreamHandlerFactory, Closeable {
    companion object {
        const val PROTOCOL = "mock"
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @Suppress("TooGenericExceptionThrown")
        private fun forceSetURLStreamHandlerFactory(factory: URLStreamHandlerFactory?) {
            try {
                URL.setURLStreamHandlerFactory(factory)
            } catch (e: Error) {
                log.info("Forcefully setting handler factory due to: ${e.message}")
                // Working around the fact that factory may have already been set once
                // by using reflection to force assign the value
                try {
                    val factoryField = URL::class.java.getDeclaredField("factory")
                    factoryField.isAccessible = true
                    factoryField.set(null, factory)

                    val handlersField = URL::class.java.getDeclaredField("handlers")
                    handlersField.isAccessible = true
                    (handlersField.get(null) as Hashtable<*, *>).clear()
                } catch (e1: NoSuchFieldException) {
                    throw Error("Could not access factory field on URL class: {}", e)
                } catch (e1: IllegalAccessException) {
                    throw Error("Could not access factory field on URL class: {}", e)
                }
            }
        }

        private class TestHttpURLConnection(private val entryValue: String) : HttpURLConnection(null) {
            override fun getInputStream(): InputStream {
                return ByteArrayInputStream(entryValue.toByteArray())
            }

            override fun getResponseCode(): Int {
                return 200
            }

            override fun connect() {
            }

            override fun disconnect() {
            }

            override fun usingProxy(): Boolean {
                return false
            }
        }
    }

    private val dummyContent: Map<String, HttpURLConnection> = content.map {
        val urlConnection = TestHttpURLConnection(it.value)
        it.key to urlConnection
    }.toMap()

    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        return if (PROTOCOL == protocol) object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection? {
                return if (dummyContent.containsKey(url.toString())) {
                    dummyContent.getValue(url.toString())
                } else {
                    null
                }
            }
        } else null
    }

    fun register() {
        log.info("Register start")
        forceSetURLStreamHandlerFactory(this)
        log.info("Register end")
    }

    override fun close() {
        log.info("Close start")
        forceSetURLStreamHandlerFactory(null)
        log.info("Close end")
    }
}
