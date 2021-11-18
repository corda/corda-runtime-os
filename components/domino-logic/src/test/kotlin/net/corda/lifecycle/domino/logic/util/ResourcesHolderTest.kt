package net.corda.lifecycle.domino.logic.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException

class ResourcesHolderTest {
    private val holder = ResourcesHolder()

    @Test
    fun `close will close the resources in the correct holder`() {
        val closed = mutableListOf<Int>()
        holder.keep {
            closed.add(1)
        }
        holder.keep {
            closed.add(2)
        }
        holder.keep {
            closed.add(3)
        }

        holder.close()

        assertThat(closed).isEqualTo(listOf(3, 2, 1))
    }

    @Test
    fun `close will close resource that was added during closing`() {
        val closed = mutableListOf<Int>()
        holder.keep {
            closed.add(1)
        }
        holder.keep {
            holder.keep {
                closed.add(2)
            }
        }

        holder.close()

        assertThat(closed).isEqualTo(listOf(2, 1))
    }

    @Test
    fun `close will ignore error during closing`() {
        val closed = mutableListOf<Int>()
        holder.keep {
            closed.add(10)
        }
        holder.keep {
            throw IOException("")
        }
        holder.keep {
            closed.add(20)
        }

        holder.close()

        assertThat(closed).isEqualTo(listOf(20, 10))
    }
}
