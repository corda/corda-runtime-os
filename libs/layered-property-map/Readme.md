# Layered Property Map

The layered property map is an immutable wrapper around a Map<String, String> and provides simple conversions to a possibly complex objects which can use several keys in dot-notation. Take as an example the map:
```
"corda.name" to "CN=me, O=R3, L=Dublin, C=Ireland",
"corda.sessionKey" to "ABCDEF...",
"corda.endpoints.0.url" to "localhost",
"corda.endpoints.0.protocolVersion" to "1",
"corda.endpoints.1.url" to "localhost",
"corda.endpoints.1.protocolVersion" to "2"
```
That map can be parsed into:
- MemberX500Name using parse("corda.name", MemberX500Name::class.java)
- session PublicKey using parse("corda.sessionKey", PublicKey::class.java)
- list of endpoints using parseList("corda.endpoints", EndpointInfo::class.java)

The default implementation `LayeredPropertyMapImpl` is extendable by supplying implementations of `CustomPropertyConverter` as OSGi component in one of your modules. 

Out of box it supports conversion to simple types like Int, Boolean, as well as MemberX500Name.

## Example of defining custom interface for your layered property map

```kotlin
interface MemberContext : LayeredPropertyMap

class MemberContextImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, MemberContext {
    override fun hashCode(): Int {
        return map.hashCode()
    }
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MemberContextImpl) return false
        return map == other.map
    }
}
```
The equality override is optional and only needed if you want to have instance comparison based on the content of the map.

## Creating instances of the map

You must use `LayeredPropertyMapFactory` to create instances of the layered map. It is OSGi component.

If you have a custom interface for your map the best way would be to use the extension function `LayeredPropertyMapFactory.create` where you would need to supply the concrete implementation of your interface like:

```kotlin
val instance = layeredPropertyMapFactory.create<MemberContextImpl>(
    getContextMap(it.value.signedMemberInfo.memberContext)
)
```

Otherwise, you can use `createMap` on the interface.

## Example of converters

```kotlin
@Component(service = [CustomPropertyConverter::class])
class PublicKeyConverter @Activate constructor(
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : CustomPropertyConverter<PublicKey> {
    override val type: Class<PublicKey>
        get() = PublicKey::class.java
    override fun convert(context: ConversionContext): PublicKey? =
        context.value()?.let { keyEncodingService.decodePublicKey(it) }
}
```

```kotlin
@Component(service = [CustomPropertyConverter::class])
class PublicKeyHashConverter : CustomPropertyConverter<PublicKeyHash> {
    override val type: Class<PublicKeyHash>
        get() = PublicKeyHash::class.java
    override fun convert(context: ConversionContext): PublicKeyHash? =
        context.value()?.let { PublicKeyHash.parse(it) }
}
```

```kotlin
class EndpointInfoConverter : CustomPropertyConverter<EndpointInfo> {
    companion object {
        private const val URL_KEY = "connectionURL"
        private const val PROTOCOL_VERSION_KEY = "protocolVersion"
    }
    override val type: Class<EndpointInfo>
        get() = EndpointInfo::class.java
    override fun convert(context: ConversionContext): EndpointInfo =
        EndpointInfoImpl(
            context.value(URL_KEY)
                ?: throw ValueNotFoundException("'$URL_KEY' is null or absent."),
            context.value(PROTOCOL_VERSION_KEY)?.toInt()
                ?: throw ValueNotFoundException("'$PROTOCOL_VERSION_KEY'is null or absent.")
        )
}
```
Note as the `EndpointInfoConverter` converts complex object (occupying several map items) in uses the `fun value(subKey: String)` overload.