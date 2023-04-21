package net.corda.v5.base.types;

import net.corda.v5.base.exceptions.ValueNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for supporting {@code Map<String, String>} structure.
 * Has the required functions for converting and parsing the String values to Objects.
 * <p>
 * The layered property map provides simple conversions to a possibly complex objects which can use several keys in
 * dot-notation. Take as an example of the map:
 * <ul>
 * <li>"corda.name" to "CN=me, O=R3, L=Dublin, C=IE",</li>
 * <li>"corda.sessionKey" to "-----BEGIN PUBLIC KEY-----Base64–encoded public key-----END PUBLIC KEY-----",</li>
 * <li>"corda.endpoints.0.url" to "localhost",</li>
 * <li>"corda.endpoints.0.protocolVersion" to "1",</li>
 * <li>"corda.endpoints.1.url" to "localhost",</li>
 * <li>"corda.endpoints.1.protocolVersion" to "2"</li>
 * </ul>
 * That map can be parsed into:
 * <ul>
 * <li>{@link MemberX500Name} using {@code parse("corda.name", MemberX500Name.class)}</li>
 * <li>session {@link PublicKey} using {@code parse("corda.sessionKey", PublicKey.class)}</li>
 * <li>list of endpoints using {@code parseList("corda.endpoints", EndpointInfo.class)}</li>
 * </ul>
 * Example usages:
 * <ul>
 * <li>Java:<pre>{@code
 * Set<Map.Entry<String, String>> entries = propertyMap.getEntries();
 * String groupId = propertyMap.parse("corda.groupId", String.class);
 * Instant modifiedTime = propertyMap.parseOrNull("corda.modifiedTime", Instant.class);
 * Set<String> additionalInformation = propertyMap.parseSet("additional.names", String.class);
 * List<EndpointInfo> endpoints = propertyMap.parseList("corda.endpoints", EndpointInfo.class);
 * }</pre></li>
 * <li>Kotlin:<pre>{@code
 * val entries = propertyMap.entries
 * val groupId = propertyMap.parse("corda.groupId", String::class.java)
 * val modifiedTime = propertyMap.parseOrNull("corda.modifiedTime", Instant::class.java)
 * val additionalInformation = propertyMap.parseSet("additional.names", String::class.java)
 * val endpoints = propertyMap.parseList("corda.endpoints", EndpointInfo::class.java)
 * }</pre></li>
 * </ul>
 *
 * The default implementation of the {@link LayeredPropertyMap} is extendable by supplying implementations of custom
 * converters using OSGi. Out of box it supports conversion to simple types like {@code int}, {@code boolean},
 * as well as {@link MemberX500Name}.
 */
public interface LayeredPropertyMap {
    /**
     * @return {@link Set} of all entries in the underlying map.
     */
    @NotNull
    Set<Map.Entry<String, String>> getEntries();

    /**
     * Finds the value of the given key in the entries.
     *
     * @param key Key for the entry we are looking for.
     *
     * @return The value of the given key or null if the key doesn't exist.
     */
    @Nullable
    String get(@NotNull String key);

    /**
     * Converts the value of the given key to the specified type.
     *
     * @param key Key for the entry we are looking for.
     * @param clazz The type of the value we want to convert to.
     *
     * @throws IllegalArgumentException if the [T] is not supported or the {@code key} is blank string.
     * @throws ValueNotFoundException if the key is not found or the value for the key is {@code null}.
     * @throws ClassCastException as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     *
     * @return The parsed values for given type.
     */
    @NotNull
    <T> T parse(@NotNull String key, @NotNull Class<? extends T> clazz);

    /**
     * Converts the value of the given key to the specified type or returns null if the key is not found or the value
     * itself is {@code null}.
     *
     * @param key Key for the entry we are looking for.
     * @param clazz The type of the value we want to convert to.
     *
     * @throws IllegalArgumentException if the [T] is not supported or the {@code key} is blank string.
     * @throws ClassCastException as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     *
     * @return The parsed values for given type or null if the key doesn't exist.
     * */
    @Nullable
    <T> T parseOrNull(@NotNull String key, @NotNull Class<? extends T> clazz);

    /**
     * Converts several items with the given prefix to the list.
     * <p>
     * Here is an example how a list will look like
     * (the {@code itemKeyPrefix} have to be "corda.endpoints" or "corda.endpoints."):
     * <pre>{@code
     *  corda.endpoints.1.url = localhost
     *  corda.endpoints.1.protocolVersion = 1
     *  corda.endpoints.2.url = localhost
     *  corda.endpoints.2.protocolVersion = 1
     *  corda.endpoints.3.url = localhost
     *  corda.endpoints.3.protocolVersion = 1
     * }</pre>
     *
     * @param itemKeyPrefix Prefix of the key for the entry we are looking for.
     * @param clazz The type of the elements in the list.
     *
     * @throws IllegalArgumentException if the [T] is not supported or the {@code itemKeyPrefix} is blank string.
     * @throws ValueNotFoundException if one of the list values is null.
     * @throws ClassCastException as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     *
     * @return A parsed list of elements for given type.
     */
    @NotNull
    <T> List<T> parseList(@NotNull String itemKeyPrefix, @NotNull Class<? extends T> clazz);

    /**
     * Converts several items with the given prefix to {@link Set}.
     * <p>
     * Here is an example of what a set will look like
     * (the {@code itemKeyPrefix} has to be "corda.ledgerKeyHashes" or "corda.ledgerKeyHashes."):
     * <pre>{@code
     *  corda.ledgerKeyHashes.1 = <hash value of ledger key 1>
     *  corda.ledgerKeyHashes.2 = <hash value of ledger key 2>
     *  corda.ledgerKeyHashes.3 = <hash value of ledger key 3>
     * }</pre>
     *
     * @param itemKeyPrefix Prefix of the key for the entry we are looking for.
     * @param clazz The type of the elements in the set.
     *
     * @throws IllegalArgumentException if the [T] is not supported or the {@code itemKeyPrefix} is blank string.
     * @throws ValueNotFoundException if one of the list values is null.
     * @throws ClassCastException as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     *
     * @return A parsed set of elements for given type.
     */
    @NotNull
    <T> Set<T> parseSet(@NotNull String itemKeyPrefix, @NotNull Class<? extends T> clazz);
}
