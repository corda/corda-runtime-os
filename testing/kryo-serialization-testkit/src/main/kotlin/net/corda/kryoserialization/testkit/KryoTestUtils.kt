package net.corda.kryoserialization.testkit

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.internal.serialization.encoding.EncoderServiceFactory
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

fun createCheckpointSerializer(
    serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> = emptyMap(),
    singletonInstances: List<SingletonSerializeAsToken> = emptyList(),
    extraClasses: Set<Class<*>> = emptySet()
): KryoCheckpointSerializer {
    val singletonSerializer = SingletonSerializeAsTokenSerializer(singletonInstances.associateBy { it.tokenName })
    val sandboxGroup = mockSandboxGroup(serializers.keys + singletonInstances.map { it::class.java } + extraClasses)
    val checkpointSerializer =
        KryoCheckpointSerializerBuilderImpl(
            CipherSchemeMetadataImpl(),
            EncoderServiceFactory(),
            sandboxGroup
        ) { classResolver ->
            Kryo(classResolver, MapReferenceResolver()).also { kryo ->
                kryo.addDefaultSerializer(SingletonSerializeAsToken::class.java, singletonSerializer)
            }
        }.let { builder ->
            builder.addSingletonSerializableInstances(singletonInstances.toSet())
            builder.addSingletonSerializableInstances(setOf(sandboxGroup))
            serializers.forEach { (clazz, serializer) -> builder.addSerializer(clazz, serializer) }

            builder.build()
        }
    return checkpointSerializer
}

fun mockSandboxGroup(taggedClasses: Set<Class<*>>): SandboxGroup {
    return mock<SandboxGroup>().apply {
        var index = 0
        val bundleClasses = taggedClasses.associateByTo(mutableMapOf()) { "${index++}" }
        val tagCaptor = argumentCaptor<Class<*>>()
        whenever(getStaticTag(tagCaptor.capture())).thenAnswer {
            val clazz = tagCaptor.lastValue
            if ((clazz.isJvmClass || clazz.isLambda) && (bundleClasses.putIfAbsent(index.toString(), clazz) == null)) {
                ++index
            }
            bundleClasses.keys.firstOrNull { value -> bundleClasses[value] == clazz }?.toString()
                ?: throw SandboxException("Class ${clazz.name} was not loaded from any bundle.")
        }
        val classCaptor = argumentCaptor<String>()
        whenever(getClass(any(), classCaptor.capture())).thenAnswer {
            val className = classCaptor.lastValue
            bundleClasses[className]
                ?: throw SandboxException("Class $className was not loaded from any bundle.")
        }
    }
}

private val Class<*>.isJvmClass: Boolean
    get() = isJavaType || (isArray && componentType.isJavaType)

private val Class<*>.isJavaType: Boolean
    get() = isPrimitive || name.startsWith("java.")

private val Class<*>.isLambda: Boolean
    get() = isSynthetic && name.contains('/')
