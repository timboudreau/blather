Blather - A Netty Based Websocket Client and Test Harness
=========================================================

Blather is a Netty-based Java websocket client, with a test harness that makes
it trivial to write JUnit tests for websocket communication.

It emphasizes simplicity and functional interfaces. Code being worth a thousand
words, here is a trivial client that just connects, sends "hello" and then
"goodbye " with whatever response it got appended:

```java
public class WebsocketTest {
    public static void main(String[] args) {
        Blather.create().client("ws://foo.example/websocket").sendOnConnect("hello")
                .onMessage(String.class, WebsocketTest::onMessage);
    }
    public static Object onMessage(int msgIndex, String inboundMessage, ChannelControl ctrl) {
        return "goodbye " + inboundMessage;
    }
}
```

Note the use of JDK 8 member references to separate the logic of connecting from the
code that has the conversation.

To use, add the Maven repository as [described here](https://timboudreau.com/builds/) and

```xml
<dependency>
    <artifactId>blather</artifactId>
    <groupId>com.mastfrog</groupId>
    <version>2.0.1-dev</version>
</dependency>
```

Manipulating The Connection
---------------------------

The `ChannelControl` interface passed into all callbacks allows you to:

 * Asynchronously send messages to the server, rather than sending them as the return
value
 * Close the connection (politely, sending a close frame)
 * Replace the current handler with another one - so if your connection has various
modes - perhaps its own handshaking phase followed by other communication - you can
simply hand off message handling to a different handler by calling `ChannelControl.nextCallback()`.

Data Marshalling
----------------

Blather uses [Jackson](https://github.com/FasterXML/jackson) for data marshalling for non-string
types (note that Jackson can speak more than JSON - for example, [Bson4Jackson](https://github.com/michel-kraemer/bson4jackson)).
So, you'll note that the message handler method above simply returned `Object` - you have
flexibility here - you can return

 * A `String` or any `CharSequence`, which will be marshalled to a plain text websocket frame
 * A Netty `WebSocketFrame`, which will be sent as-is
 * Any other object, which will be marshalled using Jackson (you can supply and configure the
`ObjectMapper` using oen of the other factory methods on `Blather`.
 * Directly get the Netty `Channel` object to do as you wish with

Exception Handling
------------------

One of the main use cases is writing unit- or functional-tests, so out of the box exception
handling is dealt with in a particular way:

 * In a test, simply call `WebsocketClientRequest.await()`, and any exception thrown will cause
the connection to be closed, and the exception will be rethrown in the main thread (any
subsequent exceptions will show up as suppressed exceptions in the stack trace of the first)

 * For other environments (where you don't have a thread blocked waiting for request completion - 
this is an asynchronous library after all), provide a `WebsocketErrorHandler` to 
`WebsocketClientRequest.withErrorHandler()` and that can decide whether to close the
connection or not.

Test Harness
============

There is a `test-jar` Maven artifact which includes a test harness for writing tests which
automatically start a server.  While designed with [Acteur](https://github.com/timboudreau/acteur) in
mind, the `Server` and `ServerControl` interfaces are 
[trivial to implement](https://timboudreau.com/builds/job/mastfrog-parent/lastSuccessfulBuild/artifact/acteur-modules/acteur-parent/acteur-util/target/apidocs/com/mastfrog/acteur/util/Server.html).

Here is a test which starts a small Acteur server, makes a websocket connection, does some
stuff and closes it:

```java
@RunWith(GuiceRunner.class)
@TestWith(TestApplication.Module.class)
public class HarnessTest {
    int count = 0;
    @Test(timeout=20000)
    public void test(WebsocketHostClient client) throws Throwable {
        client.request("/ws")
                .log()
                .addHeader(Headers.stringHeader("X-Foo"), "bar")
                .addUrlQueryPair("foo", "bar")
                .onMessage(String.class, this::withFrame)
                .await(Duration.ofSeconds(20));
    }

    public Object withFrame(int msgIndex, String data, ChannelControl ctrl) {
        if (count++ == 4) {
            ctrl.close();
            fail("Handler should have been replaced");
            return null;
        } else if (count == 3) {
            ctrl.nextCallback(this::bypass, String.class);
        }
        return "Hello: " + data;
    }

    public Object bypass(int msgIndex, String data, ChannelControl ctrl) {
        System.out.println("BYPASS GOT " + data);
        ctrl.close();
        return null;
    }
}
```

The test harness also has the ability, with Acteur (or anything that wants to
inject an `ErrorInterceptor` and call it on errors) to catch server-side exceptions
and rethrow those at the end of a test, so that server side errors are not opaque
to the test, and they simply show up as test failures with a stack trace.
