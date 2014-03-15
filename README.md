# lein-npm

Leiningen plugin for enabling Node based ClojureScript projects.

## Installation

To enable lein-npm for your project, put the following in the
`:plugins` vector of your `project.clj` file:

![Latest version](https://clojars.org/lein-npm/latest-version.svg)

## Managing NPM dependencies

You can specify a project's NPM dependencies by adding a
`:node-dependencies` key in your `project.clj`:

```clojure
:node-dependencies [[underscore "1.4.3"]
                    [nyancat "0.0.3"]
                    [mongodb "1.2.7"]]
```

These dependencies, and any `:node-dependencies` of packages pulled in
through the regular `:dependencies`, will be installed through NPM
when you run either `lein npm install` or `lein deps`.

## Invoking NPM

You can execute NPM commands that require the presence of a
`package.json` file using the `lein npm` command. This command creates
a temporary `package.json` based on your `project.clj` before invoking
the NPM command you specify. The keys `name`, `description`, `version` and
`dependencies` are automatically added to `package.json`. Other keys can be
specified by adding a `:nodejs` key in your `project.clj`:

```clojure
  :nodejs {:scripts {:test "testem"}}
```

```sh
$ lein npm install        # installs project dependencies
$ lein npm ls             # lists installed dependencies
$ lein npm search nyancat # searches for packages containing "nyancat"
```

## Bower dependencies

[lein-bower](https://github.com/chlorinejs/lein-bower) is a related
Leiningen plugin that performs the same service for
[Bower](https://github.com/twitter/bower) dependencies. lein-bower
itself depends on lein-npm.

## Running ClojureScript apps

The plugin installs a `lein run` hook which enables you to specify a
JavaScript file instead of a Clojure namespace for the `:main` key in
your `project.clj`, like this:

```clojure
:main "js/main.js"
```

If `:main` is a string that refers to a file that exists and ends with
`.js`, it will launch this file using Node, after first running `npm
install` if necessary. Any command line arguments following `lein run`
will be passed through to the Node process.

## License

Copyright 2012 Bodil Stokke

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at
[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0).

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License.
