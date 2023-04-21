package net.corda.flow.pipeline.runner

import net.corda.flow.pipeline.runner.impl.remoteToLocalContextMapper
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.toMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RemoteToLocalContextMapperTest {

    private val userContext = KeyValueStore().apply {
        this["user"] = "user"
        this["corda.user"] = "user2"
        this["corda.initiator.user"] = "user3"
    }
    private val platformContext = KeyValueStore().apply {
        this["platform"] = "platform"
        this["corda.platform"] = "platform2"
        this["corda.initiator.platform"] = "platform3"
    }

    @Test
    fun `mapping test`() {
        val localContextProperties = remoteToLocalContextMapper(
            remoteUserContextProperties = userContext.avro,
            remotePlatformContextProperties = platformContext.avro
        )

        assertThat(localContextProperties.platformProperties.items.size).isEqualTo(2)
        assertThat(localContextProperties.userProperties.items.size).isEqualTo(2)
        assertThat(localContextProperties.counterpartySessionProperties.size).isEqualTo(2)

        assertThat(localContextProperties.platformProperties.toMap())
            .isEqualTo(localContextProperties.counterpartySessionProperties)

        assertThat(localContextProperties.userProperties.toMap()["corda.initiator.user"]).isEqualTo("user2")
        assertThat(localContextProperties.platformProperties.toMap()["corda.initiator.platform"]).isEqualTo("platform2")
        assertThat(localContextProperties.userProperties.toMap()["user"]).isEqualTo("user")
        assertThat(localContextProperties.platformProperties.toMap()["platform"]).isEqualTo("platform")
    }
}
