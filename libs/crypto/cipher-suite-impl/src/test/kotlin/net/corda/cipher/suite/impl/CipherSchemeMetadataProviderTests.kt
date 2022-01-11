package net.corda.cipher.suite.impl

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CipherSchemeMetadataProviderTests {
    private lateinit var provider: CipherSchemeMetadataProviderImpl

    @BeforeEach
    fun setup() {
        provider = CipherSchemeMetadataProviderImpl()
    }

    @Test
    @Timeout(5)
    fun `Should provide always the same instance`() {
        val o1 = provider.getInstance()
        val o2 = provider.getInstance()
        val o3 = provider.getInstance()
        assertSame(o1, o2)
        assertSame(o1, o3)
    }

    @Test
    @Timeout(5)
    fun `Should use default name`() {
        assertEquals("default", provider.name)
    }
}