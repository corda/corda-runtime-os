package net.corda.membership.datamodel

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class RegistrationRequestEntityTest {

    private companion object {
        const val HOLDING_ID_1 = "1B229F6F6C88"
        const val HOLDING_ID_2 = "58B6030FABDD"
        const val SERIAL = 0L

        const val REGISTRATION_STATUS_1 = "status_1"
        const val REGISTRATION_STATUS_2 = "status_2"

        const val KEY_1 = "key_1"
        const val VALUE_1 = "value_1"
        const val KEY_2 = "key_2"
        const val VALUE_2 = "value_2"

        val randomId: String get() = UUID.randomUUID().toString()
        val currentInstant: Instant get() = Instant.now()

        fun getRegistrationContext(context: Pair<String, String>): ByteArray = getRegistrationContext(listOf(context))
        fun getRegistrationContext(context: List<Pair<String, String>>): ByteArray =
            with(KeyValuePairList.newBuilder()) {
                items = context.map { KeyValuePair(it.first, it.second) }
                build().toByteBuffer().array()
            }
    }

    @Test
    fun `entities are equal if registration id matches`() {
        val registrationId = randomId
        val e1 = RegistrationRequestEntity(
            registrationId,
            HOLDING_ID_1,
            REGISTRATION_STATUS_1,
            currentInstant,
            currentInstant,
            getRegistrationContext(KEY_1 to VALUE_1),
            SERIAL,
        )
        val e2 = RegistrationRequestEntity(
            registrationId,
            HOLDING_ID_2,
            REGISTRATION_STATUS_2,
            currentInstant.minusSeconds(5),
            currentInstant.minusSeconds(5),
            getRegistrationContext(KEY_2 to VALUE_2),
            SERIAL,
        )
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `entities are not equal if registration id does not match`() {
        val e1 = RegistrationRequestEntity(
            randomId,
            HOLDING_ID_1,
            REGISTRATION_STATUS_1,
            currentInstant,
            currentInstant,
            getRegistrationContext(KEY_1 to VALUE_1),
            SERIAL,
        )
        val e2 = RegistrationRequestEntity(
            randomId,
            HOLDING_ID_2,
            REGISTRATION_STATUS_2,
            currentInstant.minusSeconds(5),
            currentInstant.minusSeconds(5),
            getRegistrationContext(KEY_2 to VALUE_2),
            SERIAL,
        )
        assertNotEquals(e1, e2)
        assertNotEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `same instance is equal`() {
        val e1 = RegistrationRequestEntity(
            randomId,
            HOLDING_ID_1,
            REGISTRATION_STATUS_1,
            currentInstant,
            currentInstant,
            getRegistrationContext(KEY_1 to VALUE_1),
            SERIAL,
        )
        assertEquals(e1, e1)
        assertEquals(e1.hashCode(), e1.hashCode())
    }

    @Test
    fun `same instance is not equal to null`() {
        val e1 = RegistrationRequestEntity(
            randomId,
            HOLDING_ID_1,
            REGISTRATION_STATUS_1,
            currentInstant,
            currentInstant,
            getRegistrationContext(KEY_1 to VALUE_1),
            SERIAL,
        )
        assertNotEquals(e1, null)
    }

    @Test
    fun `same instance is not equal to different class type`() {
        val e1 = RegistrationRequestEntity(
            randomId,
            HOLDING_ID_1,
            REGISTRATION_STATUS_1,
            currentInstant,
            currentInstant,
            getRegistrationContext(KEY_1 to VALUE_1),
            SERIAL,
        )
        assertNotEquals(e1, "")
    }
}