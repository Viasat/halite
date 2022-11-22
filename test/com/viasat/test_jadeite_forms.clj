;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.test-jadeite-forms
  (:require [clojure.test :refer :all]
            [com.viasat.halite :as halite]
            [com.viasat.jadeite :as jadeite])
  (:import (clojure.lang ExceptionInfo)))

(def senv {:ws/A$v1 {:fields {:x :Integer
                              :y :Boolean
                              :c :ws2/B$v1}}
           :ws2/B$v1 {:fields {:s :String}}
           :ws/C$v1 {:fields {:xs [:Vec :Integer]}}
           :ws/D$v1 {:fields {:xss [:Vec [:Vec :Integer]]}}
           :ws/OhNo$v1 {:fields {:true :Integer
                                 :false :String}}})

(def tenv (halite/type-env
           {'a [:Instance :ws/A$v1]
            'b [:Instance :ws/B$v1]
            'c [:Instance :ws/C$v1]
            'ohno [:Instance :ws/OhNo$v1]
            'xb :Boolean
            'yb :Boolean
            'i :Integer
            'mi [:Maybe :Integer]
            'j :Integer
            'if :String
            'reduce :String
            'str :Integer
            'xs [:Vec :String]
            'iset [:Set :Integer]}))

(def halite-jadeite-pairs
  '[(a "a")
    (5 "5")
    (-123 "-123")
    (#d "1.23" "#d \"1.23\"")
    (#d "-1.2" "#d \"-1.2\"")
    ((rescale #d "1.23" 1) "rescale(#d \"1.23\", 1)")
    (true "true")
    (false "false")
    ("hi" "\"hi\"")
    ((div i 2) "(i / 2)")

    (if "if")
    ((if (> str 5) (str "str") (reduce [if []] [in xs] (conj if in)))
     "if(str > 5) str(\"str\") else reduce(if = []; in in xs) if.conj(in)")

    ((get a :x) "a.x")
    ((get xs (+ 2 i)) "xs[2 + i]")
    ((get (get a :c) :s) "a.c.s")
    ((get-in a [:c :s]) "a.c.s")
    ((= (get a :x) (get-in c [:xs 0])) "a.x == c.xs[0]")
    ((= (get-in a [:x]) (get-in c [:xs (- 5 2)])) "a.x == c.xs[5 - 2]")
    ^:skip-type-check ^:exact
    ((get-in a [:b :c (+ (get-in d [:e 2]) 3) :f]) "a.b.c[(d.e[2] + 3)].f")

    ((cond xb
           1
           2)
     "if(xb)
        1
      else
        2")
    ((cond xb
           (= (+ i j) 3)
           (and (= (mod i 2) 1)
                (> j 3)
                (not (or (not= j 1)
                         (= i 3)))
                true))
     "if(xb)
        (i + j == 3)
      else
        (i % 2 == 1
         && j > 3
         && !(j != 1 || i == 3)
         && true)")
    ((cond xb
           (= (+ i j) 3)
           yb
           25
           (and (= (mod i 2) 1)
                (> j 3)
                (not (or (not= j 1)
                         (= i 3)))
                true))
     "if(xb) 
          {((i + j) == 3)}
       else if(yb)
          25
        else 
         (((i % 2) == 1) && (j > 3) && !((j != 1) || (i == 3)) && true)")

    (#{}               "#{}")
    ([]                "[]")
    ([1 2 3]           "[1, 2, 3]")
    (#{1 2 3}          "#{ 1, 2, 3 }")
    ([[]]              "[[]]")
    ([#{} #{"foo"}]    "[ #{}, #{ \"foo\" } ]")

    (#{[] #{}}                       "#{ #{  }, [  ] }")
    (#{[1] #{}}                      "#{ #{  }, [1] }")
    (#{[] [1] [2]}                "#{ [], [ 2 ], [ 1 ] }")
    ([[] [1] [2]]                 "[ [], [ 1 ], [ 2 ] ]")
    ({:$type :ws/C$v1 :xs []}        "{$type: ws/C$v1, xs: []}")
    ({:$type :ws/C$v1 :xs []}        "{$type: <ws/C$v1>, xs: []}")
    ({:$type :ws/C$v1 :xs [1 2 3]}   "{xs: [1,2,3], $type:ws/C$v1}")
    ({:$type :ws/C$v1 :xs [1 2 3]}   "{$type: <ws/C$v1>, xs: [1, 2, 3]}")
    ([{:$type :ws2/B$v1 :s "bar"}]   "[ {$type: <ws2/B$v1>, s: \"bar\"} ]")
    ({:$type :ws/D$v1 :xss [[]]}     "{$type: <ws/D$v1>, xss: [ [] ]}")

    ({:$type :ws/A$v1 :x 1 :y true, :c {:$type :ws2/B$v1 :s "bar"}}
     "{$type: <ws/A$v1>, x: 1, y: true, c: {$type: <ws2/B$v1>, s: \"bar\"}}")

    ("string" "\"string\"")
    ("" "\"\"")
    ("Bob said \"Test a Quote\"" "\"Bob said \\\"Test a Quote\\\"\"")

    ((+ 1 2)  "(1 + 2)")
    ((+ 1 -2) "1+-2")
    ((- 5 3)  "(5 - 3)")
    ((- 5 -3) "5--3")
    ((+ #d "1.1" #d "0.2") "#d \"1.1\" + #d \"0.2\"")
    ((< 1 2)  "(1 < 2)")
    ((< 2 1)  "(2 < 1)")
    ((> 1 2)  "(1 > 2)")
    ((> 2 1)  "(2 > 1)")
    ((<= 1 1) "(1 <= 1)")
    ((>= 1 1) "(1 >= 1)")
    ((=> true false)  "true => false")   ;; true => false
    ((=> true true)   "true => true")
    ((=> false false) "false => false")
    ((=> false true)  "false => true")
    ^:exact
    ((and true false true) "(true && false && true)")
    ^:exact
    ((or true false false) "(true || false || false)")
    ((or false false) "(false || false)")
    ((not false) "!false")
    ((not true) "!true")
    ^:exact
    ((+ (- 1 2) (+ 3 4 5) 6) "((1 - 2) + (3 + 4 + 5) + 6)")

    ((and (<= (+ 3 5) (* 2 2 2)) (or (> 0 1) (<= (count #{1 2}) 3)))
     "(((3 + 5) <= (2 * 2 * 2)) && ((0 > 1) || (#{ 1, 2 }.count() <= 3)))")

    ((contains? #{1 2 3} 2)        "#{ 1, 2, 3 }.contains?(2)")
    ((dec i)                       "(i - 1)")
    ((div 14 3)                    "(14 / 3)")
    ((div #d "1.4" 3)                  "(#d \"1.4\" / 3)")
    ((mod 5 3)                     "(5 % 3)")
    ((expt 2 8)                    "expt(2,8)")
    ((abs -5)                      "abs(-5)")
    ((abs #d "-5.1")               "abs(#d \"-5.1\")")
    ((str "foo" (str) (str "bar")) "str(\"foo\",str(),str(\"bar\"))")

    ((= 1 2 3) "equalTo(1,2,3)")
    ((not= 1 2 3) "notEqualTo(1,2,3)")

    ((subset? #{} #{1 2 3})        "#{}.subset?(#{1,2,3})")
    ((subset? #{"nope"} #{1 2 3})  "#{ \"nope\" }.subset?(#{ 1, 2, 3 })")

    ((get [4 5] 1) "[4,5][1]")
    ((get [4] 5) "[4][5]")
    ((get xs 12) "xs[12]")
    ((get (if xb [4 5] [6 7]) 1) "(if(xb) [4, 5] else [6, 7])[1]")
    ((+ 7 (get-in {:$type :ws/C$v1 :xs [3]} [:xs 0])) "7 + {$type: <ws/C$v1>, xs: [3]}.xs[0]")
    ((+ (let [a 9] (- a 2)) 3) "({a=9; a-2}) + 3")

    ((= 1 (+ 2 3))           "(1 == (2 + 3))")
    ((= [#{}] [#{1} #{2}])   "([ #{  } ] == [ #{ 1 }, #{ 2 } ])")
    ((= [#{12}] [#{}])       "([ #{ 12 } ] == [ #{  } ])")
    ((not= [#{12}] [#{}])    "([ #{ 12 } ] != [ #{  } ])")
    ((= (* 2 3) (+ 2 4))     "((2 * 3) == (2 + 4))")
    ((= [1 2 3] [2 1 3])     "([ 1, 2, 3 ] == [ 2, 1, 3 ])")
    ((not= (* 2 3) (+ 2 4))  "((2 * 3) != (2 + 4))")
    ((not= [1 2 3] [2 1 3])  "([ 1, 2, 3 ] != [ 2, 1, 3 ])")
    ((if true 1 2)           "if(true) 1 else 2")
    ((if false [[]] [[1]])   "if(false) [[]] else [[1]]")
    ((if (get a :y) (let [x 10] (+ x (get a :x))) 99)
     "if(a.y) {x = 10; x + a.x} else 99")
    ((if (if true (> 1 2) (< 1 2)) (if false 3 4) (if true 5 6))
     "if(if(true) 1>2 else 1<2) (if(false) 3 else 4) else if(true) 5 else 6")
    ((when true 12)
     "when(true) 12")
    ^:skip-type-check
    ((if-value x (if (> x 50) "large" "small") "unset")
     "ifValue( x ) if( x > 50 ) \"large\" else \"small\" else \"unset\"")
    ^:skip-type-check
    ((if-value- x (if (> x 50) "large" "small") "unset") ;; deprecated
     "ifValue( x ) if( x > 50 ) \"large\" else \"small\" else \"unset\"")
    ^:skip-type-check
    ((if-value x (if (> x 50) "large" (if true "small" "none")) "unset")
     "(ifValue(x) {(if((x > 50)) {\"large\"} else {(if(true) {\"small\"} else {\"none\"})})} else {\"unset\"})")
    ((when-value mi (+ mi 10)) "whenValue(mi) mi + 10")
    ^:skip-type-check
    ((when-value $no-value 42) "whenValue(<$no-value>) 42")

    ((let [x (+ 1 2) y [x]] y)               "{ x = 1+2; y = [x]; y }")
    ((let [x "foo" y (let [x 1] (+ x 2))] y) "{ x = \"foo\"; y = { x=1; x+2 }; y }")
    ((let [a (let [b 5] b)] a)               "{ a = { b = 5; b }; a }")
    ((let [a 1] (+ a (let [b 2] b)))         "{ a = 1; a + ({ b = 2; b }) }")

    ^:skip-type-check
    ((if-value-let [y $no-value] 42 "foo")   "ifValueLet(y = <$no-value>) 42 else \"foo\"")
    ((when-value-let [y $no-value] 42)   "whenValueLet(y = <$no-value>) 42")
    ((if-value-let [y mi] (if-value mi 4 5) "foo")
     "ifValueLet(y = mi) ifValue(mi) 4 else 5 else \"foo\"")
    ((when-value-let [y mi] (if-value mi 4 5))
     "whenValueLet(y = mi) ifValue(mi) 4 else 5")
    ((if-value-let [y (if true 10 $no-value)] y 5) "ifValueLet( y = if(true) 10 else <$no-value>) y else 5")
    ((when-value-let [y (if true 10 $no-value)] y) "whenValueLet( y = if(true) 10 else <$no-value>) y")
    ((if-value-let [inst (valid {:$type :ws2/B$v1, :s "foo"})] (get inst :s) "not valid")
     "ifValueLet( inst = valid {$type: ws2/B$v1, s: \"foo\"}) inst.s else \"not valid\"")
    ((when-value-let [inst (valid {:$type :ws2/B$v1, :s "foo"})] (get inst :s))
     "whenValueLet( inst = valid {$type: ws2/B$v1, s: \"foo\"}) inst.s")

    ((not= 5 (get (refine-to b :ws/A$v1) :x))     "(5 != b.refineTo(ws/A$v1).x)")
    ((not= 5 (get (refine-to b :ws/A$v1) :x))     "(5 != b.refineTo( <ws/A$v1> ).x)")
    ((get (refine-to (get a :c) :ws2/B$v1) :s)    "a.c.refineTo( <ws2/B$v1> ).s")

    ((union #{1 2 3} iset)        "#{ 1, 2, 3 }.union(iset)")
    ((intersection #{1 2 3} iset) "#{ 1, 2, 3 }.intersection(iset)")
    ((difference #{1 2 3} iset)   "#{ 1, 2, 3 }.difference(iset)")
    ((= #{1 2 3} iset)            "(#{ 1, 2, 3 } == iset)")
    ((count #{1 2 3}) "#{1, 2, 3}.count()")

    ((every? [x #{1 2 3}] (and (> x 2) (< x 10)))             "every?(x in #{1,2,3}) { x>2 && x < 10}")
    ((any? [y #{1 2 3}] (or (> y 2) (< y 11)))                "any?(y in #{1,2,3}) ( y > 2 || y < 11 )")
    ((if (any? [a-b #{1 2 3}] (< a-b 3)) "yep" "nope")        "(if(any?(<a-b> in #{1, 2, 3})(<a-b> < 3)) \"yep\" else \"nope\")")
    ((every? [in [1 2]] (let [in (inc in)] (= 0 (mod in 2)))) "every?(in in [1, 2]){ in=in+1; 0 == in%2 }")
    ((map [x #{1 3 2}] (+ x 10))                              "map(x in #{ 1, 2, 3 }) x + 10")
    ((filter [x #{1 3 2}] (and (> x 2) (< x 10)))             "filter(x in #{ 1, 2, 3 }) { x > 2 && x < 10 }")
    ((range 2 7 3)                                            "range(2, 7, 3)")
    ((sort #{1 3 2})                                          "#{1,3,2}.sort()")
    ((sort-by [x #{1 3 2}] x)                                 "sortBy(x in #{1,3,2}) { x }")
    ((sort-by [x (union #{[3 1]} #{[5 0]})] (get x 1))        "sortBy(x in #{[3,1]}.union(#{[5,0]})) x[1]")
    ((reduce [acc 0] [x [1 2 3]] (+ (* 10 acc) x))            "reduce(acc = 0; x in [1, 2, 3]) { 10*acc+x }")

    ((refines-to? a :ws2/B$v1) "a.refinesTo?(ws2/B$v1)")
    ((refines-to? a :ws2/B$v1) "a.refinesTo?( <ws2/B$v1> )")
    ((valid? {:$type :ws2/B$v1, :s "foo"}) "valid? {$type: ws2/B$v1, s: \"foo\"} ")
    ((valid {:$type :ws2/B$v1, :s "foo"}) "valid {$type: ws2/B$v1, s: \"foo\"} ")
    ({:$type :ws/C$v1 :xs (conj (get c :xs) 10)} "{$type: ws/C$v1, xs: c.xs.conj(10)}")
    ((concat [] [1 2 3]) "[].concat([1,2,3])")

    ((+ 4 6) " 4 + // comment \n  6 ")
    ((+ 4 6) " 4 // comment 1 \n // another comment \n + 6")
    ((+ 4 6) " 4 + 6 // comment ")
    ((let [if 5] (if true 1 if)) "{ if = 5; if(true) 1 else if }")

    ^:skip-type-check
    ((let [true 5] true) "{ true = 5; true }")
    ((get-in a [:c :s]) "<a> .c. <s>")
    ((get-in a [:c :s]) "a.<c> . <s>")
    ((str) "<str>()")
    ((+ 5 (get ohno :true)) "5 + ohno.true")
    ((let [> 5] (> > 10)) "{ <>> = 5; <>> > 10 }")
    ((let [else 5] (if false 3 else)) "{ else = 5; if (false) 3 else else }")

    ((error "unacceptable") "error(\"unacceptable\")")
    ^:skip-type-check
    ((error (str "unacceptable: " x)) "error(str(\"unacceptable: \", x))")
    ^:skip-type-check
    ((if-value x (+ x 5) (error "unacceptable")) "ifValue(x) x+5 else error(\"unacceptable\")")])

(deftest test-pairs
  (doseq [[halite jadeite :as pair] halite-jadeite-pairs]
    (testing (str "  halite: " (pr-str halite)
                  "\n  jadeite: " jadeite
                  "\n  pair line " (:line (meta pair)))
      (try
        (let [hj (jadeite/to-jadeite halite)
              jh (jadeite/to-halite jadeite)
              hjh (jadeite/to-halite hj)
              jhj (jadeite/to-jadeite jh)]
          (when (:exact (meta pair))
            (is (= jadeite hj))
            (is (= halite jh)))
          (when-not (is (= jh hjh))
            (println :hj hj))
          (when-not (is (= hj jhj))
            (prn :jh jh))

          (when-not (:skip-type-check (meta pair))
            (let [ht  (halite/type-check senv tenv halite)
                  jht (halite/type-check senv tenv jh)]
              (is (= jht ht)))))
        (catch Exception ex
          (is (= :no-exception ex)))))))

;; Examples used in the docs
(def jadeite-eval-pairs
  '[("[ 1, 2+3, 4 ]" [1 5 4])
    ("9 / 4 == 2   // evaluates to true" true)
    ("9 % 4 == 1   // evaluates to true" true)
    ("5 == 5   // true " true)
    ("5 == 10  // false" false)
    ("[1,2,3] == [1,2,3] // true" true)
    ("[1,2,3] == [3,2,1] // false because vectors are ordered" false)
    ("#{1,2,3} == #{1,2,3} // true" true)
    ("#{1,2,3} == #{3,2,1} // true because sets are unordered" true)
    ("[#{1, 2}, #{3, 4}] == [#{2, 1}, #{4, 3}] // true" true)
    ("if(1 < 2) \"less\" else \"greater\"    // evaluates to the string \"less\"" "less")
    ("if(1 < 2 && 2 < 1) #{} else #{5}   // evaluates to the set #{5})" #{5})
    ("(if([1] == [1]) 7 else 0) + (if([2] == [3]) 5 else 3)  // 7 + 3 == 10" 10)
    ("{
  intVector = [ 1, 2, 3];
  stringVector = [ \"Chuck\", \"Morgan\", \"Sarah\"];

  [
    stringVector[1] == \"Morgan\", // true
    intVector.conj( 99 ) == [1, 2, 3, 99], // also true
    intVector.concat([4, 5, 6]) == [1, 2, 3, 4, 5, 6],  // true again
]}" [true true true])

    ("{
  intSet = #{ 30, 10, 20 };
  stringSet = #{ \"Chuck\", \"Morgan\", \"Sarah\" };

  [
    stringSet.contains?(\"Morgan\"),  // true
    stringSet.contains?( 20 ),      // FALSE!
    intSet.conj( 99 ) == #{ 99, 30, 20, 10 },          // also true
    intSet.concat(#{1,2,3}) == #{10, 20, 30, 1, 2, 3}, // true again
    #{ 5, 10, 15 }.subset?( #{10, 15} ),         // FALSE
    #{ 5, 10, 15 }.subset?( #{5, 10, 15, 20 } ), // true
  ]
}"    [true false true true false true])

    ("{
  my_a = {
    $type: ws/A$v1,
    x: 70,
    y: true,
    c: { $type: ws2/B$v1, s: \"string value\" }
  };

  my_a.x   // evaluates to 70
}"     70)

    ("if( 5 > 0 ) {
  [1, 2, 3].conj(4)
}
else {
  []
}"    [1 2 3 4])
    ("({
  a = 3;
  b = a * 100;   // so b is 300
  c = a + b;     // c is initialized as 303
  c * 2          // the whole block evaluates to 606
}) + 20           // final result is 626
"     626)])

(deftest test-jadeite-eval
  (doseq [[jadeite expected-val :as pair] jadeite-eval-pairs]
    (testing (str "jadeite line " (:line (meta pair)) ": " jadeite)
      (try
        (let [jh (jadeite/to-halite jadeite)]
          (when-not (and
                     (is (halite/type-check senv (halite/type-env {}) jh))
                     (is (= expected-val
                            (halite/eval-expr senv (halite/type-env {}) (halite/env {}) jh))))
            (prn :halite jh)))
        (catch Exception ex
          (is (= :no-exception ex)))))))

(deftest test-parse-failure
  (is (thrown-with-msg? RuntimeException #"Parse error"
                        (jadeite/to-halite ".@ 4")))

  (is (thrown-with-msg? Exception #"No such method"
                        (jadeite/to-halite "-5.abs()")))

  (is (thrown-with-msg? Exception #"No such global function"
                        (jadeite/to-halite "refineTo(a, b)"))))

(deftest test-parse-metadata
  (is (nil?
       (meta (jadeite/to-halite "4"))))
  (is (nil?
       (meta (jadeite/to-halite "\"hello\""))))
  (is (nil?
       (meta (jadeite/to-halite "#d \"1.23\""))))
  (is (= {:row 1, :col 1, :end-row 1, :end-col 19}
         (meta (jadeite/to-halite "{$type: test/C$v1}"))))
  (is (= {:row 1, :col 1, :end-row 1, :end-col 11}
         (meta (jadeite/to-halite "#{1, 2, 3}"))))
  (is (= {:row 1, :col 1, :end-row 1, :end-col 6}
         (meta (jadeite/to-halite "4 + 3"))))
  (is (= {:row 1, :col 1, :end-row 3, :end-col 2}
         (meta (jadeite/to-halite "4
+
3"))))

  (is (= {:row 1, :col 1, :end-row 1, :end-col 11}
         (meta (jadeite/to-halite "x + y == 3"))))
  (is (= {:row 1, :col 1, :end-row 1, :end-col 6}
         (meta (second (jadeite/to-halite "x + y == 3"))))))

(deftest test-equalTo
  (is (= '(= 1 2)
         (jadeite/to-halite "equalTo(1,2)"))))

(deftest test-reserved-words
  (is (= '$no-value
         (jadeite/to-halite "<$no-value>")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "$no-value")))
  (is (= '$this
         (jadeite/to-halite "<$this>")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "$this"))))

(deftest test-statements
  (is (= {} ;; this is an empty instance, which halite will reject
         (jadeite/to-halite "{}")))

  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "{x = 1")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "{x = 1;")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "{x = 1; y = 2")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "{x = 1; y = 2;")))
  (is (= '(let [x 1] x)
         (jadeite/to-halite "{x = 1; x}")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "{x = 1; x;}")))
  (is (= '(let [x 1] (error "fail"))
         (jadeite/to-halite "{x = 1; error(\"fail\")}")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "{x = 1; error(\"fail\"); x}")))
  (is (= '(let [x 1 y 2] x)
         (jadeite/to-halite "{x = 1; y = 2; x}")))
  (is (thrown-with-msg? Throwable #"Parse error"
                        (jadeite/to-halite "{x = 1; y = 2; x; z = 3}"))))

(deftest test-get-in
  (is (= '(get-in x [1 :name :age 2 :date])
         (jadeite/to-halite "x[1].name.age[2].date"))))

(deftest test-escaping-strings
  (is (= "x.a"
         (jadeite/to-jadeite '(get x :a))))
  (is (= "x.'_a'"
         (jadeite/to-jadeite '(get x :_a))))
  (is (= "x.'_'"
         (jadeite/to-jadeite '(get x :_))))
  (is (= "x.'a.b'"
         (jadeite/to-jadeite '(get x :a.b))))
  (is (= "x.a1"
         (jadeite/to-jadeite '(get x :a1))))
  (is (= "x.'a$b'"
         (jadeite/to-jadeite '(get x :a$b))))
  (is (= '(get x :_)
         (jadeite/to-halite "x.<_>")))

  (is (= "x.a[1].'_'.'$b'"
         (jadeite/to-jadeite '(get-in x [:a 1 :_ :$b]))))

  (is (= '(get-in x [:a 1 :_ :$b])
         (jadeite/to-halite "x.a[1].'_'.'$b'")))
  (is (= '(get-in x [:a 1 :_ :$b])
         (jadeite/to-halite "x.a[1].<_> .<$b>")))

  ;; this is broken due to lack of whitespace after first wrapped symbol
  (is (= '(get-in x [:a 1 :_>.<$b])
         (jadeite/to-halite "x.a[1].<_>.<$b>")))

  (is (= '(get-in x [:a :b])
         (jadeite/to-halite "x.'a'.'b'")))
  (is (= '(get-in x [:a :b])
         (jadeite/to-halite "x.<a> .<b>")))

  (is (= "{$type: ws/C$v1, '$b': 3, '_': 2, xs: 1}"
         (jadeite/to-jadeite {:$type :ws/C$v1 :xs 1 :_ 2 :$b 3})))
  (is (= {:$type :ws/C$v1, :$b 3, :_ 2, :xs 1}
         (jadeite/to-halite "{$type: ws/C$v1, '$b': 3, '_': 2, xs: 1}")))
  (is (= {:$type :ws/C$v1, :$b 3, :_ 2, :xs 1}
         (jadeite/to-halite "{$type: ws/C$v1, <$b>: 3, <_>: 2, xs: 1}"))))

(deftest test-whitespace-collection-literals-etc
  (is (= [1 2 3]
         (jadeite/to-halite "[1,2,3]")))
  (is (= #{1 2 3}
         (jadeite/to-halite "#{1,2,3}")))
  (is (thrown-with-msg? ExceptionInfo #"Parse error"
                        (jadeite/to-halite "# {1}")))
  (is (= #d "1.2"
         (jadeite/to-halite "#d \"1.2\"")))
  (is (= #d "1.2"
         (jadeite/to-halite "#d\"1.2\"")))
  (is (thrown-with-msg? ExceptionInfo #"Parse error"
                        (jadeite/to-halite "# d\"1.2\""))))

(deftest test-reduce
  (is (= '(reduce [if []] [in xs] (conj if in))
         (jadeite/to-halite "reduce(if = []; in in xs) if.conj(in)"))))

(deftest test-combine-ifs
  (is (nil?
       (#'jadeite/combine-ifs nil)))
  (is (= 1
         (#'jadeite/combine-ifs 1)))
  (is (= '(+ 1 2)
         (#'jadeite/combine-ifs '(+ 1 2))))
  (is (= '(if a b c)
         (#'jadeite/combine-ifs '(if a b c))))
  (is (= '(cond a b x y z)
         (#'jadeite/combine-ifs '(if a b (if x y z)))))
  (is (= '(cond a b x y p q r)
         (#'jadeite/combine-ifs '(if a b (if x y (if p q r)))))))

#_(deftest test-no-value
    (is (thrown-with-msg? RuntimeException #"null not allowed"
                          (jadeite/to-halite "null")))

    (is (thrown-with-msg? RuntimeException #"null not allowed"
                          (jadeite/to-halite "x == null")))
    (is (thrown-with-msg? RuntimeException #"null not allowed"
                          (jadeite/to-halite "x != null")))
    (is (= '(if-value x (+ x 2) 0)
           (jadeite/to-halite "x != null ? x + 2 : 0")))
    (is (= '(if-value x (+ x 2) 0)
           (jadeite/to-halite "null != x ? x + 2 : 0")))
    (is (= '(if-value x (+ x 2) 0)
           (jadeite/to-halite "x == null ? 0 : x + 2")))
    (is (= '(if-value x (+ x 2) 0)
           (jadeite/to-halite "null == x ? 0 : x + 2")))

    (is (= "x == null ? 0 : (x + 1)"
           (jadeite/to-jadeite '(if-value x (+ x 1) 0)))))

;; (run-tests)
