package net.corda.orm.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JpaEntitiesRegistryImplTest {
    class ExampleClass1
    class ExampleClass2
    class ExampleClass3

    private val classes1 = setOf(ExampleClass1::class.java, ExampleClass2::class.java)
    private val classes2 = setOf(ExampleClass3::class.java)

    @Test
    fun `when register can get`() {
        val registry = JpaEntitiesRegistryImpl()

        registry.register("set1", classes1)
        registry.register("set2", classes2)

        assertThat(registry.get("set1")).extracting {
            assertThat(it).isNotNull
            assertThat(it?.persistenceUnitName).isEqualTo("set1")
            assertThat(it?.classes).isEqualTo(classes1)
        }
    }

    @Test
    fun `when register can get all`() {
        val registry = JpaEntitiesRegistryImpl()

        registry.register("set1", classes1)
        registry.register("set2", classes2)

        assertThat(registry.all.map{
            it.persistenceUnitName to it.classes
        }).containsExactlyInAnyOrder(
            "set1" to classes1,
            "set2" to classes2,
        )
    }
}