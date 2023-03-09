package net.corda.kryoserialization.testkit

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import java.util.Arrays
import java.util.Collections

fun createCheckpointSerializer(
    serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> = emptyMap(),
    singletonInstances: List<SingletonSerializeAsToken> = emptyList(),
    extraClasses: Set<Class<*>> = emptySet()
): KryoCheckpointSerializer {
    val singletonSerializer = SingletonSerializeAsTokenSerializer(singletonInstances.associateBy { it.tokenName })
    val sandboxGroup = mockSandboxGroup(serializers.keys + singletonInstances.map { it::class.java } + extraClasses)
    val kryo = Kryo(MapReferenceResolver())
    kryo.addDefaultSerializer(SingletonSerializeAsToken::class.java, singletonSerializer)
    val checkpointSerializer =
        KryoCheckpointSerializerBuilderImpl(CipherSchemeMetadataImpl(), sandboxGroup, kryo).let { builder ->
            builder.addSingletonSerializableInstances(singletonInstances.toSet())
            builder.addSingletonSerializableInstances(setOf(sandboxGroup))
            serializers.forEach { (clazz, serializer) -> builder.addSerializer(clazz, serializer) }

            builder.build()
        }
    return checkpointSerializer
}

fun mockSandboxGroup(taggedClasses: Set<Class<*>>): SandboxGroup {
    val standardClasses = listOf(
        String::class.java,
        Class::class.java,
        Arrays.asList("")::class.java,
        ArrayList::class.java,
        List::class.java,
        Collections.singletonList("")::class.java,
        ByteArray::class.java
    )
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
