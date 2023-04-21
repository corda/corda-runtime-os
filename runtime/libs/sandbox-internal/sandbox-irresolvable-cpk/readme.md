This CPK requires Corda to provide "org.apache.felix:org.apache.felix.configadmin:$felixConfigAdminVersion" as a 
dependency. Corda has a matching bundle installed, but it's a private bundle in a public sandbox, so should not be 
available to resolve against. We test for this resolution failure in the tests.