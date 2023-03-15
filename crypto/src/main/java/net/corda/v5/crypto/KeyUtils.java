package net.corda.v5.crypto;

import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Collections;
import java.util.Set;

public final class KeyUtils {

    private KeyUtils() {
        // this class is never constructed; it exists for the static methods and data only
    }

    /**
     * Checks whether <code>key</code> has any intersection with the keys in <code>otherKeys</code>, 
     * recursing into <code>key</code> (the first argument) if it is a composite key. Does not match
     * any composite keys in <code>otherKeys</code>.
     * <p/>
     * For simple non-compound public keys, this operation simply checks if the first argument occurs in the
     * second argument. If <code>key</code> is a compound key, the outcome is whether any of its leaf keys
     * are in <code>otherKeys</code>.
     * {@link PublicKey}.
     *
     * Since this function checks against compound key tree leaves, which by definition are not {@link CompositeKey}. 
     * That is why if any of the <code>otherKeys</code> is a {@link CompositeKey}, this function will not 
     * find a match, though composite keys in the <code>otherKeys</code> set is not regarded as an error; they
     * are silently ignored.
     * <p/>
     * The notion of a key being in a set is about equality, which is not the same as whether one key is 
     * fulfilled by another key. For example, a {@link CompositeKey} C could be defined to have threshold 2 and:
     * </p>
     * <ul>
     *     <li> Public key X with weight 1 </li>
     *     <li> Public key Y with weight 1 </li>
     *     <li> Public key Z with weight 2 </li>
     * </ul>
     * Then we would find that <code>isKeyInSet(C, X)</code> is true, but X would not fulfill C since C is fulilled by
     * X and Y together but not X on its. However, <code>isKeyInSet(C, Z)</code> is true, and Z fulfills C by itself.
     * 
     * @param key       the key being checked for
     * @param otherKeys an {@link Iterable} sequence of {@link PublicKey}
     * @return true if <code>key</code> is in otherKeys
     */
    public static boolean isKeyInSet(@NotNull PublicKey key, @NotNull Iterable<PublicKey> otherKeys) {
        if (key instanceof CompositeKey) {
            CompositeKey compositeKey = (CompositeKey) key;
            Set<PublicKey> leafKeys = compositeKey.getLeafKeys();
            for (PublicKey otherKey : otherKeys) {
                if (leafKeys.contains(otherKey)) return true;
            }
        } else {
            for (PublicKey otherKey : otherKeys) {
                if (otherKey.equals(key)) return true;
            }
        }
        return false;
    }

    /**
     * Return true if a set of keys fulfil the requirements of a specific key.
     * 
     * Fulfilment of a {@link CompositeKey} as <code>firstKey</code> key is checked by delegating to the <code>isFulfilledBy</code> method of that
     * compound key. It is a question of whether all the keys which match the compound keys in total have enough weight
     * to reach the threshold of the primary key. 
     * 
     * In contrast, if this is called with <code>firstKey</code> being a simple public key, the test is whether
     * <code>firstKey</code> is equal to any of the keys in <code>otherKeys</code>. Since a simple public key
     * is never considered equal to a {@link CompositeKey} we know if <code>firstKey</code> is not composite, then
     * it will not be considered fulfilled by any {@link CompositeKey} in <code>otherKeys</code>. Such cases are
     * not considered errors, so we silently ignore {@link CompositeKey}s in <code>otherKeys</code>.
     *
     * If you know you have a {@link CompositeKey} in your hand it would be simpler to call its <code>isFulfilledBy()</code> 
     * method directly. This function is intended as a utility for when you have some kind of public key, and which to 
     * check fulfilment against a set of keys, without having to handle simple and composite keys separately (i.e. this is
     * polymorphic).
     * 
     * @param firstKey  the key with the requirements
     * @param otherKeys the key to check whether requirements are fulfilled
     */
    public static boolean isKeyFulfilledBy(@NotNull PublicKey firstKey, @NotNull Iterable<PublicKey> otherKeys) {
        if (firstKey instanceof CompositeKey) {
            CompositeKey firstKeyComposite = (CompositeKey) firstKey;
            return firstKeyComposite.isFulfilledBy(otherKeys);
        }
        for (PublicKey otherKey : otherKeys) {
            if (otherKey.equals(firstKey)) return true;
        }
        return false;
    }

    /**
     * Return true if one key fulfills the requirements of another key. See the previous variant; this overload
     * is the same as calling as the variant that takes an iterable set of other keys with <code>otherKey<code>
     * as a single element iterable. 
     * 
     * Since we do not define composite keys as acceptable on the second argument of this function, this relation
     * is not reflexive, not symmetric and not transitive. 
     *
     * @param firstKey the key with the requirements
     * @param otherKey the key to check whether requirements are fulfilled
     */
    public static boolean isKeyFulfilledBy(@NotNull PublicKey firstKey, @NotNull PublicKey otherKey) {
        return isKeyFulfilledBy(firstKey,
                Collections.singleton(otherKey));
    }
}
