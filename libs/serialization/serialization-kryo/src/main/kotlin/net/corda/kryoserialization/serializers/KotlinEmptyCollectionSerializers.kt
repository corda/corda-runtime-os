package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ImmutableSerializer

object KotlinEmptyListSerializer : ImmutableSerializer<Collection<*>>() {
    override fun write(kryo: Kryo?, output: Output?, obj: Collection<*>?) {
    }

    override fun read(kryo: Kryo?, input: Input?, type: Class<out Collection<*>>?): Collection<*> {
        return emptyList<Any?>()
    }
}

object KotlinEmptySetSerializer : ImmutableSerializer<Set<*>>() {
    override fun write(kryo: Kryo?, output: Output?, obj: Set<*>?) {
    }

    override fun read(kryo: Kryo?, input: Input?, type: Class<out Set<*>>?): Set<*> {
        return emptySet<Any?>()
    }
}

object KotlinEmptyMapSerializer : ImmutableSerializer<Map<*, *>>() {
    override fun write(kryo: Kryo?, output: Output?, obj: Map<*, *>?) {
    }

    override fun read(kryo: Kryo?, input: Input?, type: Class<out Map<*, *>>?): Map<*, *> {
        return emptyMap<Any?, Any?>()
    }
}
