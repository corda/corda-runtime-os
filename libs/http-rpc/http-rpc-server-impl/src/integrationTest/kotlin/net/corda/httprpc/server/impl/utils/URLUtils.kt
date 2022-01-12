package net.corda.httprpc.server.impl.utils

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.*

internal class TestURLStreamHandlerFactory(content: Map<String, String>) : URLStreamHandlerFactory, Closeable {
    companion object {
        const val PROTOCOL = "mock"

        private fun forceSetURLStreamHandlerFactory(factory: URLStreamHandlerFactory?) {
            try {
                URL.setURLStreamHandlerFactory(factory)
            } catch (e: Error) {
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
    }

    private val dummyContent: Map<String, HttpURLConnection> = content.map {
        val urlConnection = mock<HttpURLConnection>()
        whenever(urlConnection.inputStream).thenReturn(ByteArrayInputStream(it.value.toByteArray()))
        whenever(urlConnection.responseCode).thenReturn(200)
        it.key to urlConnection
    }.toMap()

    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        return if (PROTOCOL == protocol) object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection? {
                return if(dummyContent.containsKey(url.toString())) {
                    dummyContent.getValue(url.toString())
                } else {
                    null
                }
            }
        } else null
    }

    fun register() {
        forceSetURLStreamHandlerFactory(this)
    }

    override fun close() {
        forceSetURLStreamHandlerFactory(null)
    }
}
