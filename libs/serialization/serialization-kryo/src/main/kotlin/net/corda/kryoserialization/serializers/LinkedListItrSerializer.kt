package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.reflect.Field
import java.util.*

/**
 * Also, add a [ListIterator] serializer to avoid more linked list issues.
*/
internal object LinkedListItrSerializer : Serializer<ListIterator<Any>>() {
    // Create a dummy list so that we can get the ListItr from it
    // The element type of the list doesn't matter.  The iterator is all we want
    private val DUMMY_LIST = LinkedList<Long>(listOf(1))
    fun getListItr(): Any  = DUMMY_LIST.listIterator()

    private val outerListField: Field = getListItr()::class.java.getDeclaredField("this$0").apply { isAccessible = true }

    override fun write(kryo: Kryo, output: Output, obj: ListIterator<Any>) {
        kryo.writeClassAndObject(output, outerListField.get(obj))
        output.writeInt(obj.nextIndex())
    }

    override fun read(kryo: Kryo, input: Input, type: Class<ListIterator<Any>>): ListIterator<Any> {
        val list = kryo.readClassAndObject(input) as LinkedList<*>
        val index = input.readInt()
        return list.listIterator(index)
    }
}


