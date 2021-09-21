package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import javax.security.auth.x500.X500Principal

class X500PrincipalTest {
    @Test
    fun empty() {
        serializeDeserializeAssert(X500Principal(""))
    }
    @Test
    fun withName() {
        serializeDeserializeAssert(X500Principal("O=PartyA, L=London, C=GB"))
    }
}