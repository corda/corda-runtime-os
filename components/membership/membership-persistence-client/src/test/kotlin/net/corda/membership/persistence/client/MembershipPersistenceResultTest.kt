package net.corda.membership.persistence.client

import net.corda.data.membership.db.response.query.ErrorKind
import net.corda.membership.lib.exceptions.ConflictPersistenceException
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.exceptions.NotFoundEntityPersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MembershipPersistenceResultTest {
    @Test
    fun `getOrThrow return the payload for successful`() {
        val result = MembershipPersistenceResult.Success(100)

        assertThat(result.getOrThrow()).isEqualTo(100)
    }

    @Test
    fun `getOrThrow throw persistence exception for general failure`() {
        val result = MembershipPersistenceResult.Failure<Int>("error", ErrorKind.GENERAL)

        assertThrows<MembershipPersistenceException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `getOrThrow throw invalid entity update exception for this type`() {
        val result = MembershipPersistenceResult.Failure<Int>("error", ErrorKind.INVALID_ENTITY_UPDATE)

        assertThrows<InvalidEntityUpdateException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `getOrThrow throw not found exception for this type`() {
        val result = MembershipPersistenceResult.Failure<Int>("error", ErrorKind.NOT_FOUND)

        assertThrows<NotFoundEntityPersistenceException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `getOrThrow throw conflict exception for this type`() {
        val result = MembershipPersistenceResult.Failure<Int>("error", ErrorKind.CONFLICT)

        assertThrows<ConflictPersistenceException> {
            result.getOrThrow()
        }
    }
}
