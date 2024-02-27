package net.corda.membership.lib.verifiers

import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MPV_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.StringBuilder

class GroupParametersUpdateVerifierTest {
    private companion object {
        const val CUSTOM_KEY = "ext.customKey"
        const val CUSTOM_VALUE = "customValue"
    }
    private val longString = StringBuilder().apply { for (i in 0..800) { this.append("a") } }.toString()

    private val verifier = GroupParametersUpdateVerifier()

    @Test
    fun `adding a custom parameter verifies successfully`() {
        assertThat(verifier.verify(mapOf(CUSTOM_KEY to CUSTOM_VALUE)))
            .isSameAs(GroupParametersUpdateVerifier.Result.Success)
    }

    @Test
    fun `adding too many custom parameters causes verification to fail`() {
        val bigMap = (0..100).associate { "ext.$it" to CUSTOM_VALUE }
        val result = verifier.verify(bigMap)
        assertThat(result).isInstanceOf(GroupParametersUpdateVerifier.Result.Failure::class.java)
        assertThat((result as GroupParametersUpdateVerifier.Result.Failure).reason).contains("is larger than the maximum allowed")
    }

    @Test
    fun `adding a long key causes verification to fail`() {
        val result = verifier.verify(mapOf("ext.$longString" to CUSTOM_VALUE))
        assertThat(result).isInstanceOf(GroupParametersUpdateVerifier.Result.Failure::class.java)
        assertThat((result as GroupParametersUpdateVerifier.Result.Failure).reason).contains(longString)
    }

    @Test
    fun `adding a long value causes verification to fail`() {
        val result = verifier.verify(mapOf(CUSTOM_KEY to longString))
        assertThat(result).isInstanceOf(GroupParametersUpdateVerifier.Result.Failure::class.java)
        assertThat((result as GroupParametersUpdateVerifier.Result.Failure).reason).contains(CUSTOM_KEY)
    }

    @Test
    fun `adding an invalid minimum platform version causes verification to fail`() {
        val result = verifier.verify(mapOf(MPV_KEY to "123456"))
        assertThat(result).isInstanceOf(GroupParametersUpdateVerifier.Result.Failure::class.java)
        assertThat((result as GroupParametersUpdateVerifier.Result.Failure).reason).contains(MPV_KEY)
    }

    @Test
    fun `adding a parameter other than custom or MPV causes verification to fail`() {
        val result = verifier.verify(mapOf(EPOCH_KEY to "5"))
        assertThat(result).isInstanceOf(GroupParametersUpdateVerifier.Result.Failure::class.java)
        assertThat((result as GroupParametersUpdateVerifier.Result.Failure).reason).contains("Only custom fields")
    }
}
