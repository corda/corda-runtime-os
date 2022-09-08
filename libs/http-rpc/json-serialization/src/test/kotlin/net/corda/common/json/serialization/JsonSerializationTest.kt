package net.corda.common.json.serialization
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

data class Event(val name: String, val date: Instant, val memberX500Name: MemberX500Name)

class JsonSerializationTest{

    @Test
    fun `jacksonObjectMapper should serialize Instant type as date string and X500 name as String`(){
        val df = SimpleDateFormat("dd-MM-yyyy hh:mm")
        df.timeZone = TimeZone.getTimeZone("UTC")
        val date: Date = df.parse("01-01-1970 01:00")
        val event = Event("party", date.toInstant(), MemberX500Name.Companion.parse("O=Alice, L=London, C=GB"))

        val expectedSerializedEvent = """{"name":"party","date":"1970-01-01T01:00:00Z","memberX500Name":"O=Alice, L=London, C=GB"}"""

        val serializedEvent = jacksonObjectMapper().writeValueAsString(event)
        val deserializedEvent = jacksonObjectMapper().readValue<Event>(serializedEvent)

        assertNotNull(serializedEvent)
        assertEquals(expectedSerializedEvent, serializedEvent)
        assertNotNull(deserializedEvent)
        assertEquals(deserializedEvent, event)
    }
}
