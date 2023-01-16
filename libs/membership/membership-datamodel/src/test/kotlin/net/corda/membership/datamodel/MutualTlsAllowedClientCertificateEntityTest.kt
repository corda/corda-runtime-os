package net.corda.membership.datamodel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MutualTlsAllowedClientCertificateEntityTest {
    private val entity = MutualTlsAllowedClientCertificateEntity(
        "subject",
        false,
    )

    @Test
    fun `equals return true for this`() {
        assertThat(entity.equals(entity)).isTrue
    }

    @Test
    fun `equals return false for null`() {
        assertThat(entity.equals(null)).isFalse
    }

    @Test
    fun `equals return false for another type`() {
        assertThat(entity.equals(entity.subject)).isFalse
    }

    @Test
    fun `equals return false for another subject`() {
        assertThat(entity.equals(MutualTlsAllowedClientCertificateEntity("another", false))).isFalse
    }

    @Test
    fun `equals return false for another deleted state`() {
        assertThat(entity.equals(MutualTlsAllowedClientCertificateEntity(entity.subject, true))).isFalse
    }

    @Test
    fun `equals return true for same subject`() {
        assertThat(entity.equals(MutualTlsAllowedClientCertificateEntity(entity.subject, entity.isDeleted))).isTrue
    }

    @Test
    fun `hashCode returns a valid number`() {
        assertThat(entity.hashCode())
            .isEqualTo(MutualTlsAllowedClientCertificateEntity(entity.subject, false).hashCode())
    }
}