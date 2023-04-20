package net.corda.applications.workers.workercommon.internal

import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

@Execution(ExecutionMode.SAME_THREAD)
class JavaSerialisationTest {

    class Superhero(val name: String) : Serializable

    @Test
    @Order(0)
    fun `cannot java deserialise class`() {
        val hero = Superhero("Batman")

        val heroBytes = ByteArrayOutputStream().use { bos ->
            ObjectOutputStream(bos).use { os ->
                os.writeObject(hero)
                os.flush()

                bos.toByteArray()
            }
        }

        // check it works first
        ByteArrayInputStream(heroBytes).use { bis ->
            ObjectInputStream(bis).use {
                assertDoesNotThrow {
                    it.readObject()
                }
            }
        }

        // turn off serialisation
        JavaSerialisationFilter.install()
        ByteArrayInputStream(heroBytes).use { bis ->
            ObjectInputStream(bis).use {
                assertThrows<InvalidClassException> {
                    it.readObject()
                }
            }
        }
    }

    @Test
    @Order(1)
    fun `can call install twice`() {
        assertDoesNotThrow {
            JavaSerialisationFilter.install()
        }
    }
}