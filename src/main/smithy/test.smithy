namespace example

use smithy.test#httpRequestTests
use smithy4s.api#simpleRestJson

service HelloService {
    operations: [SayHello]
}

@endpoint(hostPrefix: "{hostLabel}.prefix.")
@http(method: "POST", uri: "/")
@httpRequestTests([
    {
        id: "say_hello",
        protocol: simpleRestJson,
        params: {
            "hostLabel": "foo",
            "greeting": "Hi",
            "name": "Teddy",
            "query": "Hello there"
        },
        method: "POST",
        host: "example.com",
        resolvedHost: "foo.prefix.example.com",
        uri: "/",
        queryParams: [
            "Hi=Hello%20there"
        ],
        headers: {
            "X-Greeting": "Hi",
        },
        body: "{\"name\": \"Teddy\"}",
        bodyMediaType: "application/json"
    }
])
operation SayHello {
    input: SayHelloInput,
    output: Unit
}

@input
structure SayHelloInput {
    @required
    @hostLabel
    hostLabel: String,

    @httpHeader("X-Greeting")
    greeting: String,

    @httpQuery("Hi")
    query: String,

    name: String
}
