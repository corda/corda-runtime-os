package net.corda.schema.membership.provider;

import net.corda.schema.membership.provider.impl.MembershipSchemaProviderImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for membership schema providers.
 */
public final class MembershipSchemaProviderFactory {
    private MembershipSchemaProviderFactory() {
    }

    /**
     * Create a new membership schema provider.
     *
     * @return The new membership schema provider.
     */
    @NotNull
    public static MembershipSchemaProvider getSchemaProvider() {
        return new MembershipSchemaProviderImpl();
    }
}
