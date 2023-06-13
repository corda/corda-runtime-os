package net.corda.testing.driver;

import java.util.Set;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@SuppressWarnings("unused")
public final class AllTestsDriver extends AbstractDriver implements BeforeAllCallback, AfterAllCallback {
    public AllTestsDriver(@NotNull String schemeName, @NotNull Set<MemberX500Name> members) {
        super(schemeName, members);
    }

    public AllTestsDriver(@NotNull String schemeName, @NotNull MemberX500Name member, MemberX500Name... members) {
        this(schemeName, setOf(member, members));
    }

    public AllTestsDriver(@NotNull Set<MemberX500Name> members) {
        this(DEFAULT_SCHEME_NAME, members);
    }

    public AllTestsDriver(@NotNull MemberX500Name member, MemberX500Name... members) {
        this(setOf(member, members));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        createDriver(context);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        destroyDriver(context);
    }
}
