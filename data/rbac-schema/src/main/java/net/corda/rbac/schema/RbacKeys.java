package net.corda.rbac.schema;

/**
 * A set of constants which is used for HTTP Role Based Access Control (RBAC) checks
 */
public final class RbacKeys {
    private RbacKeys() {
    }

    /**
     * Prefix of the permission string which is targeting start flow operation
     */
    public static final String START_FLOW_PREFIX = "StartFlow";

    /**
     * Separator for permission string which sits between prefix and the wildcard expression
     */
    public static final String PREFIX_SEPARATOR = ":";

    private static final String UUID_CHARS = "[a-fA-F0-9]";
    /**
     * Regular expressions to validate common data structures as part of RBAC checks
     */
    public static final String UUID_REGEX = UUID_CHARS + "{8}-"
            + UUID_CHARS + "{4}-[1-5]"
            + UUID_CHARS + "{3}-[89aAbB]"
            + UUID_CHARS + "{3}-"
            + UUID_CHARS + "{12}";

    /**
     * vNode short hash
     */
    public static final String VNODE_SHORT_HASH_REGEX = UUID_CHARS + "{12}";

    /**
     * State of the vNode. E.g. ACTIVE or IN_MAINTENANCE.
     */
    public static final String VNODE_STATE_REGEX = "[_a-zA-Z0-9]{3,255}";

    /**
     * RBAC user name regex
     */
    public static final String USER_REGEX = "[-._@a-zA-Z0-9]{3,255}";

    // first.last@company.com is a valid username, however when encoded in the URL it will be shown as
    // first.last%40company.com
    private static final String ALLOWED_USER_URL_CHARS = "[-._a-zA-Z0-9]";
    public static final String USER_URL_REGEX = ALLOWED_USER_URL_CHARS + "{3,200}[%40]{0,3}" + ALLOWED_USER_URL_CHARS + "{0,50}";

    /**
     * Flow start client request ID
     */
    public static final String CLIENT_REQ_REGEX = "[-._A-Za-z0-9]{1,250}";

    /**
     * FQN for a flow to be started
     */
    public static final String FLOW_NAME_REGEX = "[._$a-zA-Z0-9]{1,250}";
}