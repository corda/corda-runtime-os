package net.corda.membership.impl.registration

import net.corda.membership.impl.registration.dynamic.verifiers.RegistrationContextCustomFieldsVerifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.StringBuilder

class RegistrationContextCustomFieldsVerifierTest {
    private companion object {
        val CUSTOM_KEY = "customKey"
        val CUSTOM_VALUE = "customValue"
    }
    private val fieldsVerifier = RegistrationContextCustomFieldsVerifier()
    private val longString = StringBuilder().apply { for(i in 0..128){ this.append("a") } }.toString()

    @Test
    fun `adding a custom field verifies successfully`() {
        assertThat(fieldsVerifier.verify(mapOf(CUSTOM_KEY to CUSTOM_VALUE)))
            .isSameAs(RegistrationContextCustomFieldsVerifier.Result.Success)
    }

    @Test
    fun `adding to many custom keys causes verification to fail`() {
        val bigMap = (0..100).associate { it.toString() to CUSTOM_VALUE }
        val result = fieldsVerifier.verify(bigMap)
        assertThat(result).isInstanceOf(RegistrationContextCustomFieldsVerifier.Result.Failure::class.java)
    }

    @Test
    fun `adding a long key causes verification to fail`() {
        val result = fieldsVerifier.verify(mapOf(longString to CUSTOM_VALUE))
        assertThat(result).isInstanceOf(RegistrationContextCustomFieldsVerifier.Result.Failure::class.java)
        assertThat((result as RegistrationContextCustomFieldsVerifier.Result.Failure).reason).contains(longString)
    }

    @Test
    fun `adding a long value causes verification to fail`() {
        val result = fieldsVerifier.verify(mapOf(CUSTOM_KEY to longString))
        assertThat(result).isInstanceOf(RegistrationContextCustomFieldsVerifier.Result.Failure::class.java)
        assertThat((result as RegistrationContextCustomFieldsVerifier.Result.Failure).reason).contains(CUSTOM_KEY)
    }

    @Test
    fun `adding two long values causes verification to fail`() {
        val anotherCustomKey = "CUSTOM KEY 1"
        val result = fieldsVerifier.verify(mapOf(CUSTOM_KEY to longString, anotherCustomKey to longString))
        assertThat(result).isInstanceOf(RegistrationContextCustomFieldsVerifier.Result.Failure::class.java)
        assertThat((result as RegistrationContextCustomFieldsVerifier.Result.Failure).reason).contains(CUSTOM_KEY)
        assertThat(result.reason).contains(anotherCustomKey)
    }
}