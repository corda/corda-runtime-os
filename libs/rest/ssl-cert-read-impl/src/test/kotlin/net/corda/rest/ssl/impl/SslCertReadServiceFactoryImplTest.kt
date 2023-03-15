package net.corda.rest.ssl.impl

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SslCertReadServiceFactoryImplTest {

    @Test
    fun `creates instance of SslCertReadServiceFactoryImpl`() {
        Assertions.assertTrue(SslCertReadServiceFactoryImpl().create() is SslCertReadServiceImpl)
    }
}