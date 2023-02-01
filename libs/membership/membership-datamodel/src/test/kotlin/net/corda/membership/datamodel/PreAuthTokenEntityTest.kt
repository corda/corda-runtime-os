package net.corda.membership.datamodel

import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import java.time.Instant

class PreAuthTokenEntityTest {

    private companion object {
        const val FIRST_ID = "id1"
        const val SECOND_ID = "id2"
        val instant: Instant = Instant.ofEpochMilli(100)
    }

    private val entity = PreAuthTokenEntity(FIRST_ID, "", instant, "", null, null)
    private val sameIdEntity = PreAuthTokenEntity(FIRST_ID, "", instant, "Updated", null, null)
    private val secondEntity = PreAuthTokenEntity(SECOND_ID, "", instant, "", null, null)

    @Test
    fun `equals return true for this`() {
        SoftAssertions.assertSoftly {
            it.assertThat(entity.equals(entity)).isTrue
            it.assertThat(entity.hashCode()).isEqualTo(entity.hashCode())
        }
    }

    @Test
    fun `equals return false for null`() {
        Assertions.assertThat(entity.equals(null)).isFalse
    }

    @Test
    fun `equals return false for another type`() {
        Assertions.assertThat(entity.equals(String())).isFalse
    }

    @Test
    fun `equals returns true if id is the same`() {
        SoftAssertions.assertSoftly {
            it.assertThat(entity.equals(sameIdEntity)).isTrue
            it.assertThat(entity.hashCode()).isEqualTo(sameIdEntity.hashCode())
        }
    }

    @Test
    fun `equals returns false if id is different`() {
        SoftAssertions.assertSoftly {
            it.assertThat(entity.equals(secondEntity)).isFalse
            it.assertThat(entity.hashCode()).isNotEqualTo(secondEntity.hashCode())
        }
    }
}