package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.internal.serialization.model.ConfigurableLocalTypeModel
import net.corda.internal.serialization.model.FingerPrinter
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class FingerPrinterTesting : FingerPrinter {
    private var index = 0
    private val cache = mutableMapOf<LocalTypeInformation, String>()

    override fun fingerprint(typeInformation: LocalTypeInformation): String {
        return cache.computeIfAbsent(typeInformation) { index++.toString() }
    }

    @Suppress("UNUSED")
    fun changeFingerprint(type: LocalTypeInformation) {
        cache.computeIfAbsent(type) { "" }.apply { index++.toString() }
    }
}

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class FingerPrinterTestingTests {
    companion object {
        const val VERBOSE = true
    }

    @Test
    fun testingTest() {
        val fpt = FingerPrinterTesting()
        val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
        val customSerializerRegistry: CustomSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)
        val typeModel = ConfigurableLocalTypeModel(LocalTypeModelConfigurationImpl(customSerializerRegistry))

        assertEquals("0", fpt.fingerprint(typeModel.inspect(Integer::class.java)))
        assertEquals("1", fpt.fingerprint(typeModel.inspect(String::class.java)))
        assertEquals("0", fpt.fingerprint(typeModel.inspect(Integer::class.java)))
        assertEquals("1", fpt.fingerprint(typeModel.inspect(String::class.java)))
    }

    @Test
    fun worksAsReplacement() {
        @CordaSerializable
        data class C(val a: Int, val b: Long)

        val factory = SerializerFactoryBuilder.build(
            testSerializationContext.currentSandboxGroup(),
            overrideFingerPrinter = FingerPrinterTesting()
        )

        val blob = TestSerializationOutput(VERBOSE, factory).serializeAndReturnSchema(C(1, 2L))

        assertEquals(1, blob.schema.types.size)
        assertEquals("<descriptor name=\"net.corda:0\"/>", blob.schema.types[0].descriptor.toString())
    }
}