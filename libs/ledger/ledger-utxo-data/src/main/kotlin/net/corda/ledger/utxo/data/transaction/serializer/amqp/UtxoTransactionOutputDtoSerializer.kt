package net.corda.ledger.utxo.data.transaction.serializer.amqp

import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Component
import java.util.Objects

@Component(service = [ InternalCustomSerializer::class ])
class UtxoTransactionOutputDtoSerializer : BaseProxySerializer<UtxoVisibleTransactionOutputDto, UtxoTransactionOutputDtoProxy>() {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = UtxoVisibleTransactionOutputDto::class.java

    override val proxyType
        get() = UtxoTransactionOutputDtoProxy::class.java

    override val withInheritance
        // UtxoTransactionOutputDto is a final class.
        get() = false

    override fun toProxy(obj: UtxoVisibleTransactionOutputDto): UtxoTransactionOutputDtoProxy {
        return UtxoTransactionOutputDtoProxy(
            VERSION_1,
            obj.transactionId,
            obj.leafIndex,
            obj.info,
            obj.data
        )
    }

    override fun fromProxy(proxy: UtxoTransactionOutputDtoProxy): UtxoVisibleTransactionOutputDto {
        return when (proxy.version) {
            VERSION_1 ->
                UtxoVisibleTransactionOutputDto(
                    proxy.transactionId,
                    proxy.leafIndex,
                    proxy.info,
                    proxy.data
                )
            else ->
                throw CordaRuntimeException("Unable to create UtxoTransactionOutputDto with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class UtxoTransactionOutputDtoProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for [UtxoVisibleTransactionOutputDto] serialisation.
     */
    val transactionId: String,
    val leafIndex: Int,
    val info: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionOutputDtoProxy

        if (version != other.version) return false
        if (transactionId != other.transactionId) return false
        if (leafIndex != other.leafIndex) return false
        if (!info.contentEquals(other.info)) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(version, transactionId, leafIndex, info, data)
    }
}
