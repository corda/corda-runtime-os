import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LifecycleRegistryTests {
    private val superman = LifecycleCoordinatorName("superman")
    private val batman = LifecycleCoordinatorName("batman")

    @Test
    fun `when empty componentWithStatus returns empty`() {
        val registry = object : LifecycleRegistry {
            override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> = emptyMap()

        }

        assertThat(registry.componentWithStatus(listOf(LifecycleStatus.UP))).isEmpty()
    }

    @Test
    fun `when not empty filter componentWithStatus - single match`() {
        val registry = object : LifecycleRegistry {
            override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> =
                mapOf(
                    superman to CoordinatorStatus(superman, LifecycleStatus.UP, "foo"),
                    batman to CoordinatorStatus(batman, LifecycleStatus.DOWN, "foo"),
                )

        }

        assertThat(registry.componentWithStatus(listOf(LifecycleStatus.UP))).contains(superman)
    }

    @Test
    fun `when not empty filter componentWithStatus - multiple match`() {
        val registry = object : LifecycleRegistry {
            override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> =
                mapOf(
                    superman to CoordinatorStatus(superman, LifecycleStatus.UP, "foo"),
                    batman to CoordinatorStatus(batman, LifecycleStatus.UP, "foo"),
                )

        }

        assertThat(registry.componentWithStatus(listOf(LifecycleStatus.UP)))
            .containsExactlyInAnyOrder(superman, batman)
    }

    @Test
    fun `when not empty filter componentWithStatus - multiple filter`() {
        val registry = object : LifecycleRegistry {
            override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> =
                mapOf(
                    superman to CoordinatorStatus(superman, LifecycleStatus.UP, "foo"),
                    batman to CoordinatorStatus(batman, LifecycleStatus.DOWN, "foo"),
                )

        }

        assertThat(registry.componentWithStatus(listOf(LifecycleStatus.UP, LifecycleStatus.DOWN)))
            .containsExactlyInAnyOrder(superman, batman)
    }

    @Test
    fun `when not empty filter componentWithStatus - no match`() {
        val registry = object : LifecycleRegistry {
            override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> =
                mapOf(
                    superman to CoordinatorStatus(superman, LifecycleStatus.UP, "foo"),
                    batman to CoordinatorStatus(batman, LifecycleStatus.UP, "foo"),
                )

        }

        assertThat(registry.componentWithStatus(listOf(LifecycleStatus.DOWN))).isEmpty()
    }
}