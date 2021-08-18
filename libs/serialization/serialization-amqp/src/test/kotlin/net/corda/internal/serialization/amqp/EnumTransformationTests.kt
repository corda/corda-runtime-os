package net.corda.internal.serialization.amqp

import net.corda.v5.serialization.annotations.CordaSerializationTransformEnumDefault
import net.corda.v5.serialization.annotations.CordaSerializationTransformEnumDefaults
import net.corda.v5.serialization.annotations.CordaSerializationTransformRename
import net.corda.v5.serialization.annotations.CordaSerializationTransformRenames
import net.corda.internal.serialization.model.EnumTransforms
import net.corda.internal.serialization.model.InvalidEnumTransformsException
import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class EnumTransformationTests {

    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault(old = "C", new = "D"),
            CordaSerializationTransformEnumDefault(old = "D", new = "E")
    )
    @CordaSerializationTransformRenames(
        CordaSerializationTransformRename(to = "BOB", from = "FRED"),
        CordaSerializationTransformRename(to = "FRED", from = "E")
    )
    enum class MultiOperations { A, B, C, D, BOB }

    // See https://r3-cev.atlassian.net/browse/CORDA-1497
    @Test
	fun defaultAndRename() {
        val transforms = EnumTransforms.build(
                TransformsAnnotationProcessor.getTransformsSchema(MultiOperations::class.java),
                MultiOperations::class.java.constants)

        assertThat(transforms.renames).isEqualTo(mapOf("BOB" to "FRED", "FRED" to "E"))
        assertThat(transforms.defaults).isEqualTo(mapOf("D" to "C", "E" to "D"))
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename(from = "A", to = "C"),
            CordaSerializationTransformRename(from = "B", to = "D"),
            CordaSerializationTransformRename(from = "C", to = "E"),
            CordaSerializationTransformRename(from = "E", to = "B"),
            CordaSerializationTransformRename(from = "D", to = "A")
    )
    enum class RenameCycle { A, B, C, D, E}

    @Test
	fun cycleDetection() {
        assertFailsWith<InvalidEnumTransformsException> {
            EnumTransforms.build(
                    TransformsAnnotationProcessor.getTransformsSchema(RenameCycle::class.java),
                    RenameCycle::class.java.constants)
        }
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename(from = "P", to = "Q"),
            CordaSerializationTransformRename(from = "Q", to = "R")
    )
    enum class DanglingRenames { A, B, C }

    @Test
	fun renameCycleDoesNotTerminateInConstant() {
        assertFailsWith<InvalidEnumTransformsException> {
            EnumTransforms.build(
                    TransformsAnnotationProcessor.getTransformsSchema(DanglingRenames::class.java),
                    DanglingRenames::class.java.constants)
        }
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename(from = "P", to = "Q"),
            CordaSerializationTransformRename(from = "Q", to = "R")
    )
    enum class RenamesExisting { Q, R, S }

    @Test
	fun renamesRenameExistingConstant() {
        assertFailsWith<InvalidEnumTransformsException> {
            EnumTransforms.build(
                    TransformsAnnotationProcessor.getTransformsSchema(RenamesExisting::class.java),
                    RenamesExisting::class.java.constants)
        }
    }

    private val Class<*>.constants: Map<String, Int> get() =
        enumConstants.asSequence().mapIndexed { index, constant -> constant.toString() to index }.toMap()
}