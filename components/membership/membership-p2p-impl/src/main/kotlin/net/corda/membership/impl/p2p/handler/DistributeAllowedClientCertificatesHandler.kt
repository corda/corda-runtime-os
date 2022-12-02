package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.p2p.DistributeAllowedClientCertificates
import net.corda.messaging.api.records.Record
import net.corda.p2p.GatewayAllowedClientCertificates
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_ALLOWED_CLIENT_CERTIFICATE
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import java.nio.ByteBuffer

internal class DistributeAllowedClientCertificatesHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler() {
    override fun invokeAuthenticatedMessage(header: AuthenticatedMessageHeader, payload: ByteBuffer): Record<*, *> {
        // YIFT: This is a hack, we should go via the Membership -> Link manager -> Gateway
        val certificates = avroSchemaRegistry.deserialize<DistributeAllowedClientCertificates>(payload)
        return Record(
            GATEWAY_TLS_ALLOWED_CLIENT_CERTIFICATE,
            "${header.destination.x500Name}-${header.destination.groupId}",
            GatewayAllowedClientCertificates(
                header.destination,
                certificates.allowedClientCertificates,
            )
        )
    }

}