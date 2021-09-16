package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

internal fun createCheckpointSerializer(
    serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> = emptyMap(),
    singletonInstances: List<SingletonSerializeAsToken> = emptyList()
): KryoCheckpointSerializer {
    val singletonSerializer = SingletonSerializeAsTokenSerializer(singletonInstances.associateBy { it.tokenName })
    val adaptedSerializers = serializers.mapValues { KryoCheckpointSerializerAdapter(it.value).adapt() } +
            mapOf(SingletonSerializeAsToken::class.java to singletonSerializer)
    val sandboxGroup = mockSandboxGroup(serializers.keys)

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
    return mock<SandboxGroup>().also {
        Mockito.`when`(it.getStaticTag(any())).thenReturn("-1")
        taggedClasses.forEachIndexed { index, clazz ->
            Mockito.`when`(it.getStaticTag(clazz)).thenReturn("$index")
            Mockito.`when`(it.getClass(any(), eq("$index"))).thenReturn(clazz)
        }
    }

}
