package net.corda.orm.impl

import net.corda.orm.JpaEntitiesSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class JpaEntitiesRegistryImplTest {
    private val set1 = mock<JpaEntitiesSet>()
    private val set2 = mock<JpaEntitiesSet>()

    @Test
    fun `when register can part of set`() {
        val registry = JpaEntitiesRegistryImpl()

        registry.register(set1)
        registry.register(set2)

        assertThat(registry.all).isEqualTo(setOf(set1, set2))
    }
}