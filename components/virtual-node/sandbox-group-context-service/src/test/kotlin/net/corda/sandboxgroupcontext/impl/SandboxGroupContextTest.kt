package net.corda.sandboxgroupcontext.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.putUniqueObject
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextImpl
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class Dog(override val name: String, private val noise: String) : Animal {
    override fun noise() = noise

    fun eat(): String = "Nom nom nom"
}

interface Animal {
    val name: String
    fun noise(): String
}

class Cat(override val name: String, private val noise: String) : Animal {
    override fun noise() = noise
}

inline fun <reified T : Any> SandboxGroupContext.getUniqueObject() = this.get(T::class.java.name, T::class.java)

class SandboxGroupContextTest {
    private val holdingIdentity = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "bar")
    private val cpkMetadata: CpkMetadata = Helpers.mockTrivialCpk("MAIN_BUNDLE", "example", "1.0.0").metadata

    private val cpksMetadata = setOf(cpkMetadata)

    private val virtualNodeContext = VirtualNodeContext(
        holdingIdentity,
        cpksMetadata.map { parseSecureHash("DUMMY:1234567890abcdef") }.toSet(),
        SandboxGroupType.FLOW,
        null
    )
    private lateinit var sandboxGroupContext: SandboxGroupContextImpl

    @BeforeEach
    fun beforeEach() {
        sandboxGroupContext = SandboxGroupContextImpl(virtualNodeContext, mock())
    }

    @Test
    fun `can create an simple object`() {
        val dog = Dog("Fido", "Woof!")
        sandboxGroupContext.put(dog.name, dog)

        val actualDog = sandboxGroupContext.get(dog.name, Dog::class.java)

        assertThat(actualDog).isNotNull
        assertThat(actualDog!!.name).isEqualTo(dog.name)
        assertThat(actualDog.noise()).isEqualTo(dog.noise())
        assertThat(actualDog.eat()).isEqualTo(dog.eat())
    }

    @Test
    fun `can use reified extension function put`() {
        val dog = Dog("Fido", "Woof!")
        sandboxGroupContext.putObjectByKey(dog.name, dog)

        val actualDog = sandboxGroupContext.get(dog.name, Dog::class.java)

        assertThat(actualDog).isNotNull
        assertThat(actualDog!!.name).isEqualTo(dog.name)
        assertThat(actualDog.noise()).isEqualTo(dog.noise())
        assertThat(actualDog.eat()).isEqualTo(dog.eat())
    }

    @Test
    fun `can use reified extension functions get and put`() {
        val dog = Dog("Fido", "Woof!")
        sandboxGroupContext.putObjectByKey(dog.name, dog)

        val actualDog = sandboxGroupContext.getObjectByKey<Dog>(dog.name)

        assertThat(actualDog).isNotNull
        assertThat(actualDog!!.name).isEqualTo(dog.name)
        assertThat(actualDog.noise()).isEqualTo(dog.noise())
        assertThat(actualDog.eat()).isEqualTo(dog.eat())
    }

    @Test
    fun `can use put superclass`() {
        val dog = Dog("Fido", "Woof!")
        val animal: Animal = dog

        sandboxGroupContext.putObjectByKey(animal.name, animal)

        val actualDog = sandboxGroupContext.getObjectByKey<Dog>(dog.name)

        assertThat(actualDog).isNotNull
        assertThat(actualDog!!.name).isEqualTo(dog.name)
        assertThat(actualDog.noise()).isEqualTo(dog.noise())
        assertThat(actualDog.eat()).isEqualTo(dog.eat())
    }

    @Test
    fun `can put and get multiple`() {
        val dog = Dog("Fido", "Woof!")
        val cat = Cat("Satan", "Meow!")

        val canine: Animal = dog
        val feline: Animal = cat

        sandboxGroupContext.putObjectByKey(canine.name, canine)
        sandboxGroupContext.putObjectByKey(feline.name, feline)

        val actualDog = sandboxGroupContext.getObjectByKey<Dog>(dog.name)
        val actualCat = sandboxGroupContext.getObjectByKey<Cat>(cat.name)

        assertThat(actualDog).isNotNull
        assertThat(actualDog!!).isEqualTo(dog)

        assertThat(actualCat).isNotNull
        assertThat(actualCat!!).isEqualTo(cat)
    }

    @Test
    fun `get wrong type`() {
        // Who calls their dog Dave?  Or their cat?
        val name = "Dave"
        val dog = Dog(name, "Woof!")

        val canine: Animal = dog

        sandboxGroupContext.putObjectByKey(name, canine)

        assertThrows<ClassCastException>{ sandboxGroupContext.getObjectByKey<Cat>(name) }
    }

    @Test
    fun `test put and get for unique objects`() {
        val dog = Dog("Fido", "Woof!")
        val cat = Cat("Satan", "Meow!")

        val canine: Animal = dog
        val feline: Animal = cat

        sandboxGroupContext.putUniqueObject(canine)
        sandboxGroupContext.putUniqueObject(feline)

        val context: SandboxGroupContext = sandboxGroupContext

        val actualDog = context.getUniqueObject<Dog>()
        val actualCat = context.getUniqueObject<Cat>()

        assertThat(actualDog).isNotNull
        assertThat(actualDog!!).isEqualTo(dog)

        assertThat(actualCat).isNotNull
        assertThat(actualCat!!).isEqualTo(cat)

        assertThat(actualCat).isNotEqualTo(actualDog)
    }

    @Test
    fun `put duplicate throws`() {
        val bigDog = Dog("Mastiff", "Woof!")
        val littleDog = Dog("Terrier", "WOOF!")

        val bigCanine: Animal = bigDog
        val littleCanine: Animal = littleDog

        sandboxGroupContext.putUniqueObject(littleCanine)
        assertThrows<IllegalArgumentException> { sandboxGroupContext.putUniqueObject(bigCanine) }
    }


    @Test
    fun `can put and get multiple of same class`() {
        val dog = Dog("Fido", "Woof!")
        val dog2 = Dog("Rover", "Woof!")
        val cat = Cat("Satan", "Meow!")
        val cat2 = Cat("Bastet", "Meow!")

        val canine: Animal = dog
        val canine2: Animal = dog2
        val feline: Animal = cat
        val feline2: Animal = cat2

        sandboxGroupContext.putObjectByKey(canine.name, canine)
        sandboxGroupContext.putObjectByKey(canine2.name, canine2)
        sandboxGroupContext.putObjectByKey(feline.name, feline)
        sandboxGroupContext.putObjectByKey(feline2.name, feline2)

        val actualDog = sandboxGroupContext.getObjectByKey<Dog>(dog.name)
        val actualDog2 = sandboxGroupContext.getObjectByKey<Dog>(dog2.name)
        val actualCat = sandboxGroupContext.getObjectByKey<Cat>(cat.name)
        val actualCat2 = sandboxGroupContext.getObjectByKey<Cat>(cat2.name)

        assertThat(actualDog!!).isEqualTo(dog)
        assertThat(actualDog2!!).isEqualTo(dog2)

        assertThat(actualCat!!).isEqualTo(cat)
        assertThat(actualCat2!!).isEqualTo(cat2)
    }

    @Test
    fun `can put as subclass and get superclass`() {
        // we're only using string-based keys
        val dog = Dog("Fido", "Woof!")
        val canine: Animal = dog
        sandboxGroupContext.putObjectByKey(canine.name, canine)
        val animal = sandboxGroupContext.getObjectByKey<Animal>(canine.name)
        assertThat(animal is Animal).isTrue
    }
}
