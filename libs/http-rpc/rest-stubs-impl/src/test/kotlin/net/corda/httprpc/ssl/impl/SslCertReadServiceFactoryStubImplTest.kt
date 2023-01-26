package net.corda.libs.config.net.corda.httprpc.ssl.impl

import net.corda.httprpc.ssl.impl.SslCertReadServiceFactoryStubImpl
import net.corda.httprpc.ssl.impl.SslCertReadServiceStubImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SslCertReadServiceFactoryStubImplTest {

    @Test
    fun `creates instance of SslCertReadServiceStubImpl`() {
        Assertions.assertTrue(SslCertReadServiceFactoryStubImpl().create() is SslCertReadServiceStubImpl)
    }
}