package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testResourceName
import net.corda.internal.serialization.amqp.testutils.writeTestResource
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DeprecatedConstructorForDeserialization
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@Suppress("MaxLineLength")
class EvolutionObjectBuilderRenamedPropertyTests {
    private val cordappVersionTestValue = 38854445
    private val dataTestValue = "d7af8af0-c10e-45bc-a5f7-92de432be0ef"
    private val xTestValue = 7568055

    class TestTransaction(val bob: String)

    interface TestContract {
        fun verify(tx: TestTransaction)
    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class TestBelongsToContract(val value: KClass<out TestContract>)

    class TemplateContract : TestContract {
        override fun verify(tx: TestTransaction) {}
    }

    @CordaSerializable
    abstract class TestParty(val uncle: Int)

    @CordaSerializable
    interface TestContractState {
        val participants: List<TestParty>
    }

    /**
     * Step 1
     *
     * This is the original class definition in object evolution.
     */
//    @TestBelongsToContract(TemplateContract::class)
//    @CordaSerializable
//    data class TemplateState(val cordappVersion: Int, val data: String, val x : Int?, override val participants: List<TestParty> = listOf()) : TestContractState

    /**
     * Step 2
     *
     * This is an intermediate class definition in object evolution.
     * The y property has been added and a constructor copies the value of x into y. x is now set to null by the constructor.
     */
//    @BelongsToContract(TemplateContract::class)
//    @CordaSerializable
//    data class TemplateState(val cordappVersion: Int, val data: String, val x : Int?, val y : String?, override val participants: List<AbstractParty> = listOf()) : ContractState {
//        @DeprecatedConstructorForDeserialization(1)
//        constructor(cordappVersion: Int, data : String, x : Int?, participants: List<AbstractParty>)
//                : this(cordappVersion, data, null, x?.toString(), participants)
//    }

    /**
     * Step 3
     *
     * This is the final class definition in object evolution.
     * The x property has been removed but the constructor that copies values of x into y still exists. We expect previous versions of this
     * object to pass the value of x to the constructor when deserialized.
     */
    @TestBelongsToContract(TemplateContract::class)
    @CordaSerializable
    data class TemplateState(val cordappVersion: Int, val data: String, val y: String?, override val participants: List<TestParty> = listOf()) : TestContractState {
        @DeprecatedConstructorForDeserialization(version = 1)
        constructor(
            cordappVersion: Int,
            data: String,
            x: Int?,
            participants: List<TestParty>
        ) : this(cordappVersion, data, x?.toString(), participants)
    }

    @Test
    fun step1ToStep3() {

        // The next two commented lines are how the serialized data is generated. To regenerate the data, uncomment these along
        // with the correct version of the class and rerun the test. This will generate a new file in the project resources.

//        val step1 = TemplateState(cordappVersionTestValue, dataTestValue, xTestValue)
//        saveSerializedObject(step1)

        val bytes = requireNotNull(this::class.java.getResource(testResourceName())).readBytes()

        val serializerFactory: SerializerFactory = testDefaultFactory()
        val deserializedObject = DeserializationInput(serializerFactory)
            .deserialize(SerializedBytes<TemplateState>(bytes))

        Assertions.assertThat(deserializedObject.cordappVersion).isEqualTo(cordappVersionTestValue)
        Assertions.assertThat(deserializedObject.data).isEqualTo(dataTestValue)
//        Assertions.assertThat(deserializedObject.x).isEqualTo(xTestValue)
        Assertions.assertThat(deserializedObject.y).isEqualTo(xTestValue.toString())
        Assertions.assertThat(deserializedObject).isInstanceOf(TemplateState::class.java)
    }

    /**
     * Write serialized object to resources folder
     */
    @Suppress("unused")
    fun <T : Any> saveSerializedObject(obj: T) = writeTestResource(SerializationOutput(testDefaultFactory()).serialize(obj))
}
