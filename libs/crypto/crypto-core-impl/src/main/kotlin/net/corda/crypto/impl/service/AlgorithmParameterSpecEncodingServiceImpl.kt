package net.corda.crypto.impl.service

import net.corda.crypto.core.service.AlgorithmParameterSpecEncodingService
import net.corda.crypto.core.service.AlgorithmParameterSpecSerializer
import net.corda.crypto.core.service.SerializedAlgorithmParameterSpec
import org.osgi.service.component.annotations.Component
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PSSParameterSpec

@Component(service = [AlgorithmParameterSpecEncodingService::class])
class AlgorithmParameterSpecEncodingServiceImpl : AlgorithmParameterSpecEncodingService {
    private val paramSpecSerializers = mapOf<String, AlgorithmParameterSpecSerializer<out AlgorithmParameterSpec>>(
        PSSParameterSpec::class.java.name to PSSParameterSpecSerializer()
    )

    @Suppress("UNCHECKED_CAST")
    override fun serialize(params: AlgorithmParameterSpec): SerializedAlgorithmParameterSpec {
        val clazz = params::class.java.name
        val serializer = paramSpecSerializers[clazz] as? AlgorithmParameterSpecSerializer<AlgorithmParameterSpec>
            ?: throw IllegalArgumentException("$clazz is not supported.")
        return SerializedAlgorithmParameterSpec(
            clazz = clazz,
            bytes = serializer.serialize(params)
        )
    }

    override fun deserialize(params: SerializedAlgorithmParameterSpec): AlgorithmParameterSpec {
        val serializer = paramSpecSerializers[params.clazz]
            ?: throw IllegalArgumentException("${params.clazz} is not supported.")
        return serializer.deserialize(params.bytes)
    }
}