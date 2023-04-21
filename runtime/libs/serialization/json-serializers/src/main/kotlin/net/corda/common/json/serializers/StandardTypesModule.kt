package net.corda.common.json.serializers

import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.v5.base.types.MemberX500Name

/**
 * Static entry point to stock platform serialization providers defined in this library and only in this library. This
 * is for components which use Jackson directly but want to make use of any generic Corda platform serializers created
 * in this library to avoid duplication. This entry point is not to be used for components which dynamically discover
 * platform serializers across the entire codebase. For those use cases, the platform serializers are annotated as OSGi
 * components so they are runtime discoverable.
 */
fun standardTypesModule() = SimpleModule("Standard types").apply {
    // Our serializers are based around the Corda api not the Jackson one. As we use Jackson underneath the abstraction
    // we are already able to turn Corda serializers into Jackson ones using adaptors. For exposing the Jackson versions
    // of our serializers we can return a module pre-populated with these adaptors. Thus no matter how you use these
    // serializers, Jackson or Corda abstraction, you get the benefit of not having to write multiple versions of the
    // same serializers.

    val memberX500NameDeserializer = JsonDeserializerAdaptor(MemberX500NameDeserializer(), MemberX500Name::class.java)
    // Jackson api tries to defend against adding a deserializer at compile time which is not typed the same as the
    // class it's registered against, although at runtime it doesn't make any difference. Here we have to suggest we
    // are serialising Any types because the adaptors (generally being constructed at run time) do not retain type
    // information outside a Class<*> object.
    @Suppress("unchecked_cast")
    addDeserializer(memberX500NameDeserializer.deserializingType as Class<Any>, memberX500NameDeserializer)
    addSerializer(
        MemberX500Name::class.java,
        JsonSerializerAdaptor(MemberX500NameSerializer(), MemberX500Name::class.java)
    )
}
