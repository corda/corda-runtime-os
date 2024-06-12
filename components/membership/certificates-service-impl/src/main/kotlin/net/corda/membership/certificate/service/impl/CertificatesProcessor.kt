package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.ListCertificateAliasesRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.data.certificates.rpc.response.ListCertificateAliasRpcResponse
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.messaging.api.processor.RPCResponderProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

internal class CertificatesProcessor(
    private val client: DbCertificateClient,
) : RPCResponderProcessor<CertificateRpcRequest, CertificateRpcResponse> {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CertificatesProcessor::class.java)
    }

    override fun onNext(request: CertificateRpcRequest, respFuture: CompletableFuture<CertificateRpcResponse>) {
        try {
            logger.info("CertificatesProcessor received request: $request")
            val holdingIdentity = if (request.holdingIdentity == null) {
                null
            } else {
                ShortHash.of(request.holdingIdentity)
            }
            val payload = when (val requestPayload = request.request) {
                is ImportCertificateRpcRequest -> {
                    client.importCertificates(
                        request.usage,
                        holdingIdentity,
                        requestPayload.alias,
                        requestPayload.certificates
                    )
                    CertificateImportedRpcResponse()
                }
                is RetrieveCertificateRpcRequest -> {
                    logger.info("Request type is RetrieveCertificateRpcRequest")
                    val certificates = client.retrieveCertificates(
                        holdingIdentity,
                        request.usage,
                        requestPayload.alias,
                    )
                    CertificateRetrievalRpcResponse(certificates)
                }
                is ListCertificateAliasesRpcRequest -> {
                    val aliases = client.getCertificateAliases(
                        request.usage,
                        holdingIdentity
                    ).toList()
                    ListCertificateAliasRpcResponse(aliases)
                }
                else -> {
                    throw CertificatesServiceException("Unknown request: $request")
                }
            }
            logger.info("Completing CertificateRpcResponse future with payload: $payload")
            respFuture.complete(CertificateRpcResponse(payload))
        } catch (e: Throwable) {
            logger.info("Completing CertificateRpcResponse future exceptionally with exception: $e")
            respFuture.completeExceptionally(e)
        }
    }
}
