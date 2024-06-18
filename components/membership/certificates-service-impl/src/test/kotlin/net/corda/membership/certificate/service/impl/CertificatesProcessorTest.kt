package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.ListCertificateAliasesRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.data.certificates.rpc.response.ListCertificateAliasRpcResponse
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class CertificatesProcessorTest {
    private val client = mock<DbCertificateClient>()
    private val future = CompletableFuture<CertificateRpcResponse>()

    private val processor = CertificatesProcessor(client)

    @Test
    fun `onNext will throw an exception for invalid holding identity`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            "nop",
            ListCertificateAliasesRpcRequest()
        )

        processor.onNext(request, future)

        assertThat(future).isCompletedExceptionally
    }

    @Test
    fun `onNext will call the client importCertificates if needed`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            null,
            ImportCertificateRpcRequest(
                "alias",
                "certificate",
            )
        )

        processor.onNext(request, future)

        verify(client).importCertificates(
            CertificateUsage.P2P_TLS,
            null,
            "alias",
            "certificate"
        )
    }

    @Test
    fun `onNext will return CertificateImportedRpcResponse after import`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            null,
            ImportCertificateRpcRequest(
                "alias",
                "certificate",
            )
        )

        processor.onNext(request, future)

        assertThat(future).isCompletedWithValue(CertificateRpcResponse(CertificateImportedRpcResponse()))
    }

    @Test
    fun `onNext will will complete exceptionally if client fails`() {
        whenever(
            client.importCertificates(
                any(),
                anyOrNull(),
                any(),
                any()
            )
        ).doThrow(CordaRuntimeException("Ooops"))
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            null,
            ImportCertificateRpcRequest(
                "alias",
                "certificate",
            )
        )

        processor.onNext(request, future)

        assertThat(future).isCompletedExceptionally
    }

    @Test
    fun `onNext will call the client retrieveCertificates if needed`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            "123123123213",
            RetrieveCertificateRpcRequest(
                "alias"
            )
        )

        processor.onNext(request, future)

        verify(client).retrieveCertificates(
            ShortHash.of("123123123213"),
            CertificateUsage.P2P_TLS,
            "alias",
        )
    }

    @Test
    fun `onNext will return CertificateRetrievalRpcResponse after get with the correct value`() {
        whenever(
            client.retrieveCertificates(
                ShortHash.of("123123123213"),
                CertificateUsage.CODE_SIGNER,
                "alias",
            )
        ).thenReturn("certificate")
        val request = CertificateRpcRequest(
            CertificateUsage.CODE_SIGNER,
            "123123123213",
            RetrieveCertificateRpcRequest(
                "alias"
            )
        )

        processor.onNext(request, future)

        assertThat(future).isCompletedWithValue(CertificateRpcResponse(CertificateRetrievalRpcResponse("certificate")))
    }

    @Test
    fun `onNext will call the client getCertificateAliases if needed`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            "123123123213",
            ListCertificateAliasesRpcRequest()
        )

        processor.onNext(request, future)

        verify(client).getCertificateAliases(
            CertificateUsage.P2P_TLS,
            ShortHash.of("123123123213"),
        )
    }

    @Test
    fun `onNext will return ListCertificateAliasRpcResponse after get with the correct value`() {
        whenever(
            client.getCertificateAliases(
                CertificateUsage.P2P_SESSION,
                ShortHash.of("123123123213"),
            )
        ).thenReturn(
            listOf(
                "one",
                "two",
            )
        )
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_SESSION,
            "123123123213",
            ListCertificateAliasesRpcRequest()
        )

        processor.onNext(request, future)

        assertThat(future).isCompletedWithValue(
            CertificateRpcResponse(
                ListCertificateAliasRpcResponse(
                    listOf(
                        "one",
                        "two",
                    )
                )
            )
        )
    }

    @Test
    fun `onNext will return an error for an unexpected value`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_SESSION,
            "123123123213",
            null,
        )

        processor.onNext(request, future)

        assertThat(future).isCompletedExceptionally
    }
}
