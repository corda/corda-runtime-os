package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock

internal fun createCheckpointSerializer(
    serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> = emptyMap(),
    singletonInstances: List<SingletonSerializeAsToken> = emptyList()
): KryoCheckpointSerializer {
    val singletonSerializer = SingletonSerializeAsTokenSerializer(singletonInstances.associateBy { it.tokenName })
    val adaptedSerializers = serializers.mapValues { KryoCheckpointSerializerAdapter(it.value).adapt() } +
            mapOf(SingletonSerializeAsToken::class.java to singletonSerializer)
    val sandboxGroup = mockSandboxGroup(serializers.keys + singletonInstances.map { it::class.java })

    return KryoCheckpointSerializer(
        DefaultKryoCustomizer.customize(
            Kryo(),
            adaptedSerializers,
            CordaClassResolver(sandboxGroup),
            ClassSerializer(sandboxGroup)
        )
    )
}

internal fun mockSandboxGroup(taggedClasses: Set<Class<*>>): SandboxGroup {
    val standardClasses = listOf(String::class.java, Class::class.java)
    return mock<SandboxGroup>().also {
        var index = 0
        val bundleClasses = (standardClasses + taggedClasses).associateBy { "${index++}" }
        val tagCaptor = argumentCaptor<Class<*>>()
        `when`(it.getStaticTag(tagCaptor.capture())).thenAnswer {
            bundleClasses.keys.firstOrNull { value -> bundleClasses[value] == tagCaptor.lastValue }?.toString()
                ?: throw SandboxException("Class ${tagCaptor.lastValue} was not loaded from any bundle.")
        }
        val classCaptor = argumentCaptor<String>()
        `when`(it.getClass(any(), classCaptor.capture())).thenAnswer {
            bundleClasses[classCaptor.lastValue]
                ?: throw SandboxException("Class ${tagCaptor.lastValue} was not loaded from any bundle.")
        }
    }

}
