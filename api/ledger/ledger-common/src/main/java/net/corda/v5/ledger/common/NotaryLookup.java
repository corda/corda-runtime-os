package net.corda.v5.ledger.common;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.membership.NotaryInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Defines a mechanism to allow flows to retrieve the {@link NotaryInfo} in the network.
 * The platform will provide an instance of {@link NotaryLookup} to flows via property injection.
 */
@DoNotImplement
public interface NotaryLookup {

    /**
     * Gets a {@link Collection} of {@link NotaryInfo} services available on the network.
     *
     * @return Returns a {@link Collection} of {@link NotaryInfo} services available on the network.
     */
    @NotNull
    @Suspendable
    Collection<NotaryInfo> getNotaryServices();

    /**
     * Looks up the notary information of a notary by legal name.
     *
     * @param notaryServiceName The {@link MemberX500Name} of the notary service to retrieve.
     * @return Returns the {@link NotaryInfo} that matches the specified notary service name, or null if no such notary exists.
     */
    @Nullable
    @Suspendable
    NotaryInfo lookup(@NotNull MemberX500Name notaryServiceName);

    /**
     * Determines whether the specified virtual node name is a notary, which is defined by the network parameters.
     *
     * @param virtualNodeName The {@link MemberX500Name} to check.
     * @return Returns true if the specified virtual node name is a notary; otherwise, false.
     */
    @Suspendable
    boolean isNotaryVirtualNode(@NotNull MemberX500Name virtualNodeName);
}
