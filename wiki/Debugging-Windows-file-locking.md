# Root cause and fix

The main source of all Windows locking problems are unclosed `AutoCloseable` subclasses.

This includes, but is not limited to, incorrect usage of:

* `Files.newInputStream`
* `Files.list(...)`

Ensure that these use the `.use { }` scope function or our `Path.list(block: (Stream<Path>) -> R)` extension function.

# Debugging with Linux

To find any files opened by Linux, where the file descriptor was not released, run this command:

```
ls -l /proc/<pid>/fd
```

If you are debugging an integration test, to retrieve the file descriptors:

1. Find the `pid` while grepping for the port:

   ```
   ps -cdef | grep 'address=5005'
   ```

2. Generalise that to a `watch` task:

   ```
   watch -n 1 ls -l /proc/$(ps -cdef | grep 'address=5005' | head -1 | awk '{print$2}')/fd \| grep junit
   ```

3. `grep` it for files in `junit` folders.

   You can now run the integration (or unit tests if you omit `5005`) and step through watching when file descriptors appear.
