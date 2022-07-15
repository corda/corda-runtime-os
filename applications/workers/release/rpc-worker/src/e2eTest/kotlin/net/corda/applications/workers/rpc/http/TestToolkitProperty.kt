package net.corda.applications.workers.rpc.http

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
class TestToolkitProperty(
    private val httpHost: String,
    private val httpPort: Int,
    private val kafkaHost: String,
    private val kafkaPort: Int,
) :
    ReadOnlyProperty<Any, TestToolkit> {

    companion object {
        const val DEFAULT_HTTP_HOST = "localhost"
        const val DEFAULT_HTTP_PORT = 8888
        const val DEFAULT_KAFKA_HOST = "prereqs-kafka-0-external"
        const val DEFAULT_KAFKA_PORT = 9094
    }

    constructor() : this(
        DEFAULT_HTTP_HOST,
        DEFAULT_HTTP_PORT,
        DEFAULT_KAFKA_HOST,
        DEFAULT_KAFKA_PORT,
    )

    private lateinit var impl: TestToolkit

    override fun getValue(thisRef: Any, property: KProperty<*>): TestToolkit {
        if (!this::impl.isInitialized) {
            impl = TestToolkitImpl(
                thisRef.javaClass,
                "https://$httpHost:$httpPort/api/v1/",
                "$kafkaHost:$kafkaPort"
            )
        }

        return impl
    }
}