package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializer
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.mockito.kotlin.mock

internal fun createCheckpointSerializer(
    serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> = emptyMap(),
    singletonInstances: List<SingletonSerializeAsToken> = emptyList()
): KryoCheckpointSerializer {
    return KryoCheckpointSerializer(
        DefaultKryoCustomizer.customize(
            Kryo(),
            serializers,
            CordaClassResolver(mock(), mock(), mock()),
            ClassSerializer(mock(), mock(), mock()),
            SingletonSerializeAsTokenSerializer(singletonInstances.associateBy { it.tokenName })
        )
    )
}
