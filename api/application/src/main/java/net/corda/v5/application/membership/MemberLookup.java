package net.corda.v5.application.membership;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.membership.MemberInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;
import java.util.List;

/**
 * {@link MemberLookup} allows flows to retrieve the {@link MemberInfo} for any member of the network, including itself.
 * <p>
 * The platform will provide an instance of {@link MemberLookup} to flows via property injection.
 */
@DoNotImplement
public interface MemberLookup {

    /**
     * Returns the {@link MemberInfo} for the calling flow.
     */
    @Suspendable
    @NotNull
    MemberInfo myInfo();

    /**
     * Returns the {@link MemberInfo} with the specific X500 name.
     *
     * @param name The {@link MemberX500Name} name of the member to retrieve.
     *
     * @return An instance of {@link MemberInfo} for the given {@link MemberX500Name} name or null if no member found.
     */
    @Suspendable
    @Nullable
    MemberInfo lookup(@NotNull MemberX500Name name);

    /**
     * Returns the {@link MemberInfo} with the specific public key.
     *
     * @param key The {@link PublicKey} of the member to retrieve.
     *
     * @return An instance of {@link MemberInfo} for the given {@link PublicKey} name or null if no member found.
     */
    @Suspendable
    @Nullable
    MemberInfo lookup(@NotNull PublicKey key);

    /**
     * Returns a list of {@link MemberInfo} for all members in the network.
     *
     * @return A list of {@link MemberInfo}.
     */
    @Suspendable
    @NotNull
    List<MemberInfo> lookup();
}

