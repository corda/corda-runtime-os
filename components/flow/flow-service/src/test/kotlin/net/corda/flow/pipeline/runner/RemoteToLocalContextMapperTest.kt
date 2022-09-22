package net.corda.flow.pipeline.runner

import net.corda.flow.pipeline.runner.impl.remoteToLocalContextMapper
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.toMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RemoteToLocalContextMapperTest {

    private val userContext = KeyValueStore().apply {
        this["user"] = "user"
    }
    private val platformContext = KeyValueStore().apply {
        this["platform"] = "platform"
    }

    @Test
    fun `mapping test`() {
        val localContextProperties = remoteToLocalContextMapper(
            remoteUserContextProperties = userContext.avro,
            remotePlatformContextProperties = platformContext.avro
        )

        assertThat(localContextProperties.platformProperties).isEqualTo(platformContext.avro)
        assertThat(localContextProperties.userProperties).isEqualTo(userContext.avro)
        assertThat(localContextProperties.counterpartySessionProperties).isEqualTo(platformContext.avro.toMap())
    }
}
