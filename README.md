[![Stories in Ready](https://badge.waffle.io/tailrecursion/castra.png?label=ready&title=Ready)](https://waffle.io/tailrecursion/castra)
![masada][2]

# Castra

Web application RPC library for Clojure/Script and Ring.

## Installation

Artifacts are published on Clojars.

[](dependency)
```clojure
[hoplon/castra "3.0.0-alpha1"] ;; latest release
```
[](/dependency)

## The Big Picture

The purpose of Castra is to make async server calls feel like
expression evaluation, providing a more cohesive programming
experience across the front end and back end of your system. Instead
of thinking "I'm going to send this data bag in a POST to the `/xyz`
endpoint," supports you in thinking thoughts like "I'm going to
evaluate the expression `(update-record 123 {:x 1 :y 2})` on the
server."

Castra's front-end and back-end libraries implement this RPC pattern.

## Server Usage

Most of the magic happens in Castra's `castra.middleware/wrap-castra`
ring middleware. This middleware looks for an expression under the
`:body` key of the ring request map. It looks for a request that looks
something like:

```clojure
{:body ['update-record 123 {:x 1 :y 2}]}
```

It then dispatches the expression `['update-record 123 {:x 1 :y 2}]`
by attempting to call the function `update-record`. `update-record`
should be created with `castra.core/defrpc`, and returns a ring
response map with the result of `update-record` in the body and a 200
status. You can think of the response as if it were this:

```clojure
{:status 200
 :body (update-record 123 {:x 1 :y 2})}
```

The response will be sent using Transit, so it's OK for the body to be
a Clojure data structure. That's part of how Castra provides a
seamless frontend/backend programming experience.

By default, Castra's front-end functions use Transit so that, on the
server side, the request's body evaluates to a Castra-friendly
expression.

If you're not sending requests with Transit, however, then you need to
use middleware to get a Castra-friendly expression into the ring
request's body. For example, if you're uploading a file as
`multipart/form-data`, you can use ring's `wrap-multipart-params` and
write a custom middleware to put the result in the ring request body
as a Castra-friendly expression.

Your final middleware might look something like:

```clojure
(def APP
  (-> (wrap-castra 'geir-backend.core)
      (wrap-castra-session "secret-key")
      (wrap-transit-response {:encoding :json})
      (wrap-handle-file-upload) ; this is your custom code
      (wrap-keyword-params)
      (wrap-multipart-params)))
```

## Client Usage

TODO

## Examples

The [Hoplon Demos][1] repo contains demo apps using Castra.

## TODO

* explain `defrpc` and endpoints.
* explain validation

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.

[1]: https://github.com/hoplon/demos
[2]: https://raw.github.com/hoplon/castra/master/img/Masada.png
