package net.corda.common.json.serialization
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

data class Event(val name: String, val date: Instant)

class JsonSerializationTest{

    @Test
    fun `jacksonObjectMapper should serialize Instant type as date string `(){
        val df = SimpleDateFormat("dd-MM-yyyy hh:mm")
        df.timeZone = TimeZone.getTimeZone("UTC")
        val date: Date = df.parse("01-01-1970 01:00")
        val event = Event("party", date.toInstant())

        val expectedSerializedEvent = """{"name":"party","date":"1970-01-01T01:00:00Z"}"""

        val serializedEvent = jacksonObjectMapper().writeValueAsString(event)
        val deserializedEvent = jacksonObjectMapper().readValue<Event>(serializedEvent)

        assertNotNull(serializedEvent)
        assertEquals(expectedSerializedEvent, serializedEvent)
        assertNotNull(deserializedEvent)
        assertEquals(deserializedEvent, event)
    }
}
