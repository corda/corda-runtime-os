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
     * Checks whether any of the given <code>keys</code> match a leaf on the {@link CompositeKey} tree or a single
     * {@link PublicKey}.
     *
     * <i>Note that this function checks against leaves, which cannot be of type {@link CompositeKey} Due to that, if any of the
     * <code>otherKeys</code> is a {@link CompositeKey}, this function will not find a match.</i>
     *
     * @param key       the key being checked for
     * @param otherKeys an {@link Iterable} sequence of {@link PublicKey}
     * @return true if key is in otherKeys
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
     * Return true if one key fulfills the requirements of another key.
     *
     * @param firstKey the key with the requirements
     * @param otherKey the key to check whether requirements are fulfilled
     */
    public static boolean isKeyFulfilledBy(@NotNull PublicKey firstKey, @NotNull PublicKey otherKey) {
        return isKeyFulfilledBy(firstKey,
                Collections.singleton(otherKey));
    }

    /**
     * Return true if any of a set of other keys fulfil the requirements of a key
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
}
