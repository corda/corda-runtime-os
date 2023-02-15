package net.corda.membership.datamodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ApprovalRulesEntityTest {
    private companion object {
        val randomId: String get() = UUID.randomUUID().toString()
        const val rule1 = "corda.roles.*"
        const val rule2 = "^*"
        const val ruleType1 = "type1"
        const val ruleType2 = "type2"
        const val ruleLabel1 = "Roles"
        const val ruleLabel2 = "All"
    }

    @Nested
    inner class EqualityAndHashTests {
        @Test
        fun `entities are equal if rule id and rule type match`() {
            val ruleId = randomId
            val e1 = ApprovalRulesEntity(
                ruleId,
                ruleType1,
                rule1,
                ruleLabel1
            )
            val e2 = ApprovalRulesEntity(
                ruleId,
                ruleType1,
                rule2,
                ruleLabel2
            )
            assertEquals(e1, e2)
            assertEquals(e1.hashCode(), e2.hashCode())
        }

        @Test
        fun `entities are not equal if rule id does not match`() {
            val e1 = ApprovalRulesEntity(
                randomId,
                ruleType1,
                rule1,
                ruleLabel1
            )
            val e2 = ApprovalRulesEntity(
                randomId,
                ruleType1,
                rule1,
                ruleLabel1
            )
            assertNotEquals(e1, e2)
            assertNotEquals(e1.hashCode(), e2.hashCode())
        }

        @Test
        fun `entities are not equal if rule type does not match`() {
            val ruleId = randomId
            val e1 = ApprovalRulesEntity(
                ruleId,
                ruleType1,
                rule1,
                ruleLabel1
            )
            val e2 = ApprovalRulesEntity(
                ruleId,
                ruleType2,
                rule1,
                ruleLabel1
            )
            assertNotEquals(e1, e2)
            assertNotEquals(e1.hashCode(), e2.hashCode())
        }

        @Test
        fun `same instance is equal`() {
            val e1 = ApprovalRulesEntity(
                randomId,
                ruleType1,
                rule1,
                ruleLabel1
            )
            assertEquals(e1, e1)
            assertEquals(e1.hashCode(), e1.hashCode())
        }

        @Test
        fun `same instance is not equal to null`() {
            assertNotEquals(
                ApprovalRulesEntity(
                    randomId,
                    ruleType1,
                    rule1,
                    ruleLabel1
                ),
                null
            )
        }

        @Test
        fun `same instance is not equal to different class type`() {
            assertNotEquals(
                ApprovalRulesEntity(
                    randomId,
                    ruleType1,
                    rule1,
                    ruleLabel1
                ),
                ""
            )
        }
    }
}
