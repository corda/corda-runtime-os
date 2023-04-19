package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.crypto.SignatureSpec
import java.time.Instant
import java.util.SortedMap

class DigitalSignatureMetadataSerializer :
    BaseProxySerializer<DigitalSignatureMetadata, DigitalSignatureMetadataSerializer.DigitalSignatureMetadataProxy>() {

    data class DigitalSignatureMetadataProxy(
        val timestamp: Instant,
        val signatureSpec: SignatureSpec,
        val properties: SortedMap<String, String>
    )

    override fun toProxy(obj: DigitalSignatureMetadata): DigitalSignatureMetadataProxy =
        DigitalSignatureMetadataProxy(
            obj.timestamp,
            obj.signatureSpec,
            obj.properties.toSortedMap()
        )

    override fun fromProxy(proxy: DigitalSignatureMetadataProxy): DigitalSignatureMetadata =
        DigitalSignatureMetadata(
            proxy.timestamp,
            proxy.signatureSpec,
            proxy.properties
        )

    override val proxyType = DigitalSignatureMetadataProxy::class.java
    override val type = DigitalSignatureMetadata::class.java
    override val withInheritance = false
}