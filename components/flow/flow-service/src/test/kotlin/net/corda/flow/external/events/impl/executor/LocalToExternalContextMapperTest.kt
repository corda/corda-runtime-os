package net.corda.flow.external.events.impl.executor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalToExternalContextMapperTest {

    private val userContext = mapOf("userKey" to "userValue")
    private val platformContext = mapOf("platformKey" to "platformValue")

    @Test
    fun `mapping test`() {
        val externalContext = localToExternalContextMapper(
            userContextProperties = userContext,
            platformContextProperties = platformContext
        )

        assertThat(externalContext.size).isEqualTo(2)
        assertThat(externalContext["userKey"]).isEqualTo("userValue")
        assertThat(externalContext["platformKey"]).isEqualTo("platformValue")
    }
}
