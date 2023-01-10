package net.corda.testing.driver;

import java.util.Set;
import net.corda.v5.base.types.MemberX500Name;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@SuppressWarnings("unused")
public final class AllTestsDriver extends AbstractDriver implements BeforeAllCallback, AfterAllCallback {
    public AllTestsDriver(String schemeName, Set<MemberX500Name> members) {
        super(schemeName, members);
    }

    public AllTestsDriver(String schemeName, MemberX500Name... members) {
        this(schemeName, Set.of(members));
    }

    public AllTestsDriver(Set<MemberX500Name> members) {
        this(DEFAULT_SCHEME_NAME, members);
    }

    public AllTestsDriver(MemberX500Name... members) {
        this(Set.of(members));
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
