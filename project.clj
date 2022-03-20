(defproject
 com.viasat/halite
 "1.0.0"
 :description
 "Successor to Salt"
 :license
 {:name "MIT", :url "https://opensource.org/licenses/MIT"}
 :dependencies
 [[aysylu/loom "1.0.2"]
  [borkdude/edamame "1.0.0"]
  [instaparse "1.4.10"]
  [net.nextencia/rrdiagram "0.9.4"]
  [org.choco-solver/choco-solver "4.10.8"]
  [org.clojure/clojure "1.11.0"]
  [org.clojure/core.match "1.0.0"]
  [org.clojure/math.numeric-tower "0.0.5"]
  [org.clojure/tools.logging "1.2.4"]
  [potemkin "0.4.5"]
  [prismatic/schema "1.4.0"]
  [weavejester/dependency "0.2.1"]]
 :profiles
 {:test-dep
  {:dependencies
   [[cheshire "5.10.1"]
    [lambdaisland/kaocha "1.65.1029"]
    [org.clojure/test.check "1.1.1"]
    [zprint "1.2.4"]]},
  :test [:test-dep],
  :dev [:test-dep]})
