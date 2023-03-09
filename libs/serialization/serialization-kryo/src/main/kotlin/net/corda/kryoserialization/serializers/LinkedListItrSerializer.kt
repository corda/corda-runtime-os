package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.reflect.Field
import java.util.*

/**
 * The [ListIterator] has a problem with the default Quasar/Kryo serialisation
 * in that serialising an iterator over a sufficiently large
 * data set can lead to a stack overflow (because the object map is traversed recursively).
 *
 * We've added our own custom serializer in order to ensure that only the key/value are recorded.
 * The rest of the list isn't required at this scope.
*/
internal object LinkedListItrSerializer : Serializer<ListIterator<*>>() {
    // Create a dummy list so that we can get the ListItr from it
    // The element type of the list doesn't matter.  The iterator is all we want
    private val DUMMY_LIST = LinkedList<Long>(listOf(1))
    fun getListItr(): Any  = DUMMY_LIST.listIterator()

    private val outerListField: Field = getListItr()::class.java.getDeclaredField("this$0").apply {
        isAccessible = true
    }

    override fun write(kryo: Kryo, output: Output, obj: ListIterator<*>) {
        kryo.writeClassAndObject(output, outerListField.get(obj))
        output.writeInt(obj.nextIndex())
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out ListIterator<*>>): ListIterator<*> {
        val list = kryo.readClassAndObject(input) as LinkedList<*>
        val index = input.readInt()
        return list.listIterator(index)
    }
}


