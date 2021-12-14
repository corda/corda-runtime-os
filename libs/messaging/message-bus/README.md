The API defined in this library is intended to provide a separation 
between the underlying message bus (in which messages are actually 
passed between workers) and the message patterns which use the bus.

In this way we can separate the message patterns from the actual mechanism
which passes the messages around.

It is not expected that this API will be implemented by anyone other than
those specifically implementing a new underlying message bus infrastructure
or maintaining existing ones.

Instead, any dependencies should be declared on `libs:messaging:messaging`.
