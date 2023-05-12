# Corda Security Profiles

Corda comes with a few predefined security profiles that can be used as provided or customized for specific needs. The strictest policy (`high_security.policy`) is applied by default, but a Corda administrator can override this policy via configuration endpoint if required. Configuration section is named `corda.security` and security policy can be set via field `policy`.

Policies are based on [Conditional Permissions](https://docs.osgi.org/specification/osgi.core/8.0.0/service.condpermadmin.html#i1534586) of OSGi security model.
They can have `ALLOW` and `DENY` access blocks that represent a string encoded [ConditionalPermissionInfo](https://docs.osgi.org/javadoc/r4v42/org/osgi/service/condpermadmin/ConditionalPermissionInfo.html#getEncoded()). Block at higher position has a higher priority. Each block starts with conditions that need to be satisfied in order to apply that block. After that comes a list of permissions that are either allowed or denied based on the block type. The [basic syntax](https://docs.osgi.org/specification/osgi.core/8.0.0/service.condpermadmin.html#i1716478) is:

```
policy      ::= access '{' conditions permissions '}' name?
access      ::= 'ALLOW' | 'DENY'       // case insensitive 
conditions  ::= ( '[' qname quoted-string* ']' )*
permissions ::= ( '(' qname (quoted-string 
                         quoted-string?)? ')' )+
name        ::= quoted-string
```

Snippet below shows an example of deny-access block for Flow sandbox:

```
DENY {
[org.osgi.service.condpermadmin.BundleLocationCondition "FLOW/*"]

(java.io.FilePermission "<<ALL FILES>>" "read,write,delete,execute,readLink")
(java.lang.RuntimePermission "getFileSystemAttributes" "")
(java.lang.RuntimePermission "readFileDescriptor" "")
(java.lang.RuntimePermission "writeFileDescriptor" "")
(java.net.SocketPermission "*:1−" "accept,listen,connect,resolve")
(java.net.URLPermission "http://*:*" "*:*")
(java.net.URLPermission "https://*:*" "*:*")
(java.lang.RuntimePermission "accessDeclaredMembers" "")
(java.lang.reflect.ReflectPermission "suppressAccessChecks" "")
(java.lang.reflect.ReflectPermission "newProxyInPackage.*" "")

} "High security profile for FLOW Sandbox"
```

For more info about permissions in JDK see https://docs.oracle.com/en/java/javase/11/security/permissions-jdk1.html.

