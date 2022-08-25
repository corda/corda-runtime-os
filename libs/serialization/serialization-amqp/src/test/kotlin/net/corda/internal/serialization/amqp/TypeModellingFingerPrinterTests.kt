package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.internal.serialization.model.ConfigurableLocalTypeModel
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.internal.serialization.model.TypeModellingFingerPrinter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotEquals

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TypeModellingFingerPrinterTests {

    val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
    val customRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)
    val fingerprinter = TypeModellingFingerPrinter(
        customRegistry,
        testSerializationContext.currentSandboxGroup(),
        true
    )

    // See https://r3-cev.atlassian.net/browse/CORDA-2266
    @Test
	fun `Object and wildcard are fingerprinted differently`() {
        val objectType = LocalTypeInformation.Top
        val anyType = LocalTypeInformation.Unknown

        assertNotEquals(fingerprinter.fingerprint(objectType), fingerprinter.fingerprint(anyType))
    }

    // Not serializable, because there is no readable property corresponding to the constructor parameter
    class NonSerializable(@Suppress("UNUSED_PARAMETER") a: String)

    class HasTypeParameter<T>
    data class SuppliesTypeParameter(val value: HasTypeParameter<NonSerializable>)

    // See https://r3-cev.atlassian.net/browse/CORDA-2848
    @Test
	fun `can fingerprint type with non-serializable type parameter`() {
        val typeModel = ConfigurableLocalTypeModel(LocalTypeModelConfigurationImpl(customRegistry))
        val typeInfo = typeModel.inspect(SuppliesTypeParameter::class.java)

        assertThat(typeInfo).isInstanceOf(LocalTypeInformation.Composable::class.java)
        val propertyTypeInfo = typeInfo.propertiesOrEmptyMap["value"]?.type as LocalTypeInformation.Composable
        assertThat(propertyTypeInfo.typeParameters[0]).isInstanceOf(LocalTypeInformation.NonComposable::class.java)

        fingerprinter.fingerprint(typeInfo)
    }
}