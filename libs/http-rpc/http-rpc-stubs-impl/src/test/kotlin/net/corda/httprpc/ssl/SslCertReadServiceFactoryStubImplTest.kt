package net.corda.httprpc.ssl

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SslCertReadServiceFactoryStubImplTest {

    @Test
    fun `creates instance of SslCertReadServiceStubImpl`() {
        assertTrue(SslCertReadServiceFactoryStubImpl().create() is SslCertReadServiceStubImpl)
    }
}