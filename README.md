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

## Usage

Castra uses Cognitect's Transit library to serialize and deserialize EDN as JSON, giving a transparent
feel for calling remote functions and abstracting the HTTP layer away at the same time.

## Examples

The [Hoplon Demos][1] repo contains demo apps using Castra.

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.

[1]: https://github.com/hoplon/demos
[2]: https://raw.github.com/hoplon/castra/master/img/Masada.png
