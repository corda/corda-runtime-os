This module contains ORM mapping classes for representing RPC RBAC data constructs.

## Integration Tests

Integration tests are not simple JUnit tests, but OSGi tests that have to be executed using Gradle as follows:

```
gradle :libs:permissions:permission-datamodel:clean :libs:permissions:permission-datamodel:integrationTest
```

Should you need to debug `-D-runjdb=5005` can be added to expose port 5005 for remote debugging.

For more information on DB integration tests, please see [here](../../db/readme.md).