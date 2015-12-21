[![Stories in Ready](https://badge.waffle.io/tailrecursion/castra.png?label=ready&title=Ready)](https://waffle.io/tailrecursion/castra)
![masada][2]

# Castra

Web application RPC library for Clojure/Script and Ring.

## Installation

Artifacts are published on Clojars.

[](dependency)
```clojure
[hoplon/castra "3.0.0-alpha2"] ;; latest release
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

RPC does come with baggage, though. Implementing an RPC framework that
provides a seamless and transparent remote execution model is a very
difficult distributed systems problem. Castra does not attempt this.
Instead, Castra's RPC model embraces the asynchronous nature of client
server communication.

## Server Usage

Most of the magic happens in Castra's `castra.middleware/wrap-castra`
ring middleware. This middleware looks for an expression under the
`:body` key of the ring request map. It looks for a request that looks
something like:

```clojure
{:request-method :post
 :body "(my.app/update-record 123 {:x 1 :y 2})"}
```

* The Castra middleware deserializes the body to obtain Clojure forms.
* It then dispatches the expression `(my.app/update-record 123 {:x 1 :y 2})`
  by resolving and attempting to call the function `my.app/update-record`.
  (This function should be created with `castra.core/defrpc`, explained below.)
* The Castra middleware then returns a ring response map with the serialized
  result in the `:body` and a 200 status.

You can think of the response as if it were this:

```clojure
{:status 200
 :body (pr-str (my.app/update-record 123 {:x 1 :y 2}))}
```

Obviously, we don't want the client to be able to evaluate arbitrary
expressions on the server (we already have [nREPL][nrepl] for that). We
want to be able to mark certain functions as part of our application's
RPC interface. This is accomplished with `castra.core/defrpc`:

```clojure
(ns my.app
  (:require
    [castra.core :as c]
    [some.database :as db]))

(c/defrpc get-record
  [id]
  (db/query "SELECT * FROM record WHERE id = ?" id))

(c/defrpc update-record
  [id {:keys [x y]}]
  (db/execute "UPDATE IN record SET x = ?, y = ? WHERE id = ?" x y id)
  (get-record id))
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
[nrepl]: https://github.com/clojure/tools.nrepl
