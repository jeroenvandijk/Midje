(ns midje.parsing.util.t-zip
  (:require [midje.parsing.util.zip :refer :all]
            [midje.sweet :refer :all]
            [midje.test-util :refer :all]
            [clojure.zip :as zip]))

(defn node [expected] (fn [actual] (= expected (zip/node actual))))

(fact "can position loc at rightmost leaf"
  (let [z (zip/seq-zip '(a b "leaf"))]
    (skip-to-rightmost-leaf (zip/down z)) => (node "leaf"))

    (let [z (zip/seq-zip '(111 (a => [1 2 '(3)]) (("leaf"))))]
      (skip-to-rightmost-leaf (zip/down z)) => (node "leaf")))


(fact "it's useful to delete a node and move right"
  (let [z (zip/seq-zip '( (f n) => (+ 3 4)))
        loc (-> z zip/down zip/right)]
    (remove-moving-right loc) => (node '(+ 3 4))
    (zip/root (remove-moving-right loc)) => '( (f n) (+ 3 4))))


