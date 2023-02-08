package net.corda.applications.workers.rest.http

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * The reason why this is implemented as extension of [ReadOnlyProperty] rather than as
 * extension from [TestToolkit] as the former gives all access to the [Class] of the
 * testcase which contains this property, giving an opportunity to "personalize" implementation
 * for a specific test.
 *
 * Such personalization may, for example, include RPC user names that are matching the name of the test class.
 */
class TestToolkitProperty(private val host: String, private val port: Int) :
    ReadOnlyProperty<Any, TestToolkit> {

    companion object {
        const val DEFAULT_HTTP_HOST = "localhost"
        const val DEFAULT_HTTP_PORT = 8888
    }

    constructor() : this(DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT)

    private lateinit var impl: TestToolkit

    override fun getValue(thisRef: Any, property: KProperty<*>): TestToolkit {
        if (!this::impl.isInitialized) {
            impl = TestToolkitImpl(thisRef.javaClass, "https://$host:$port/api/v1/")
        }

        return impl
    }
}