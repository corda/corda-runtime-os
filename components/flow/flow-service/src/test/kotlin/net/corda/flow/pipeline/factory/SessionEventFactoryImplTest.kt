package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.MessageDirection
import net.corda.flow.pipeline.factory.impl.SessionEventFactoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionEventFactoryImplTest {

    @Test
    fun `create returns populated session event`(){
        val factory = SessionEventFactoryImpl()
        val nowUtc = Instant.ofEpochMilli(1)
        val result = factory.create("s1", nowUtc, "test")

        assertThat(result.sessionId).isEqualTo("s1")
        assertThat(result.messageDirection).isEqualTo(MessageDirection.OUTBOUND)
        assertThat(result.timestamp).isEqualTo(nowUtc)
        assertThat(result.sequenceNum==null).isTrue
        assertThat(result.receivedSequenceNum).isEqualTo(0)
        assertThat(result.payload).isEqualTo("test")
    }
}