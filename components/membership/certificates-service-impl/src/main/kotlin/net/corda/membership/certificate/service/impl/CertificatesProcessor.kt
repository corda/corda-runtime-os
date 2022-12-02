package net.corda.membership.certificate.service.impl

import net.corda.data.certificates.rpc.request.AllowClientCertificate
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.DisallowClientCertificate
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.ListAllowedCertificates
import net.corda.data.certificates.rpc.request.ListCertificateAliasesRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateAllowedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateDisallowedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.data.certificates.rpc.response.ListAllowedCertificatesRpcResponse
import net.corda.data.certificates.rpc.response.ListCertificateAliasRpcResponse
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.virtualnode.ShortHash
import java.util.concurrent.CompletableFuture

internal class CertificatesProcessor(
    private val client: DbCertificateClient,
) :
    RPCResponderProcessor<CertificateRpcRequest, CertificateRpcResponse> {

    @Suppress("ComplexMethod")
    override fun onNext(request: CertificateRpcRequest, respFuture: CompletableFuture<CertificateRpcResponse>) {
        try {
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
                is AllowClientCertificate -> {
                    client.allowCertificate(
                        holdingIdentity!!,
                        requestPayload.subject
                    )
                    CertificateAllowedRpcResponse()
                }
                is DisallowClientCertificate -> {
                    client.disallowCertificate(
                        holdingIdentity!!,
                        requestPayload.subject
                    )
                    CertificateDisallowedRpcResponse()
                }
                is ListAllowedCertificates -> {
                    val subjects = client.listAllowedCertificates(
                        holdingIdentity!!,
                    )
                    ListAllowedCertificatesRpcResponse(subjects.toList())
                }
                else -> {
                    throw CertificatesServiceException("Unknown request: $request")
                }
            }
            respFuture.complete(CertificateRpcResponse(payload))
        } catch (e: Throwable) {
            respFuture.completeExceptionally(e)
        }
    }
}
