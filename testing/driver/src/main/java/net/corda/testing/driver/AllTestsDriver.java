package net.corda.testing.driver;

import java.security.KeyPair;
import java.util.Map;
import java.util.Set;
import net.corda.data.KeyValuePair;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class AllTestsDriver extends AbstractDriver implements BeforeAllCallback, AfterAllCallback {
    AllTestsDriver(
        @NotNull Map<@NotNull MemberX500Name, @NotNull KeyPair> members,
        @NotNull Map<@NotNull MemberX500Name, @NotNull KeyPair> notaries,
        @NotNull Set<@NotNull KeyValuePair> groupParameters
    ) {
        super(members, notaries, groupParameters);
    }

    @Override
    public void beforeAll(@NotNull ExtensionContext context) {
        createDriver(context);
    }

    @Override
    public void afterAll(@NotNull ExtensionContext context) throws Exception {
        destroyDriver(context);
    }
}
