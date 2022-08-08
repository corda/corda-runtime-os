package net.corda.testutils.tools

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UtilitiesTest {

    /**
     * TODO: Watch https://r3-cev.atlassian.net/browse/CORE-5987 -
     * if fixed in Corda, this should also do inherited fields.
     */
    @Test
    fun `should extend Flow with inject but only for declared fields`() {
        // Given two fields, one of which is declared and one which is not
        val b = B()

        // When we inject into both of them
        b.injectIfRequired(String::class.java, "Hello!")
        b.injectIfRequired(Any::class.java, Object())

        // Then the declared field should be set, but not the inherited one
        assertNotNull(b.declaredField)
        assertThrows<UninitializedPropertyAccessException>({b.inheritedField})
    }

    open class A : Flow {
        @CordaInject
        lateinit var inheritedField: String
    }

    class B : A() {
        @CordaInject
        lateinit var declaredField: Any
    }
}