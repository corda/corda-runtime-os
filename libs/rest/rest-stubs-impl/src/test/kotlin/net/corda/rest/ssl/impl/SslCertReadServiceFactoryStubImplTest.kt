package net.corda.libs.config.net.corda.rest.ssl.impl

import net.corda.rest.ssl.impl.SslCertReadServiceFactoryStubImpl
import net.corda.rest.ssl.impl.SslCertReadServiceStubImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SslCertReadServiceFactoryStubImplTest {

    @Test
    fun `creates instance of SslCertReadServiceStubImpl`() {
        Assertions.assertTrue(SslCertReadServiceFactoryStubImpl().create() is SslCertReadServiceStubImpl)
    }
}