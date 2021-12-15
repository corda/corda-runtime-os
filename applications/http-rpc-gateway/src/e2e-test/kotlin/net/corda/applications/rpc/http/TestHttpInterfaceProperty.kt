package net.corda.applications.rpc.http

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * The reason why this is implemented as extension of [ReadOnlyProperty] rather than as
 * extension from [TestHttpInterface] as the former gives all access to the [Class] of the
 * testcase which contains this property, giving an opportunity to "personalize" implementation
 * for a specific test.
 *
 * Such personalization may, for example, include RPC user names that are matching the name of the test class.
 */
class TestHttpInterfaceProperty(private val host: String, private val port: Int) :
    ReadOnlyProperty<Any, TestHttpInterface> {

    companion object {
        val DEFAULT_HTTP_HOST = "localhost"
        val DEFAULT_HTTP_PORT = 8888
    }

    constructor() : this(DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT)

    private lateinit var impl: TestHttpInterface

    override fun getValue(thisRef: Any, property: KProperty<*>): TestHttpInterface {
        if (!this::impl.isInitialized) {
            impl = TestHttpInterfaceImpl(thisRef.javaClass, "https://$host:$port/api/v1/")
        }

        return impl
    }
}