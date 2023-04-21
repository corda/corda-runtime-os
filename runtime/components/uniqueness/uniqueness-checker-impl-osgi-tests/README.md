This module contains integration tests for uniqueness checker implementations that are OSGi packaged. They are segregated from non-OSGi tests (which are part of their corresponding source modules) to minimise complexity.

A couple of things to bear in mind when running these tests:

- Test cases won't give nice per-test case results in a GUI like IntelliJ. You need to look at the test output and see the number of successful / failed tests.
- You must delete the contents of `build/test-results` after each test run. If you do not do this, the `integrationTest` task will report it was successful, but won't actually run the tests.
