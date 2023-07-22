This CPK requires Corda to provide libs.felix.configadmin as a 
dependency. Corda has a matching bundle installed, but it's a private bundle in a public sandbox, so should not be 
available to resolve against. We test for this resolution failure in the tests.