;; -*- indent-tabs-mode: nil -*-
(ns midje.util.report
  (:use clojure.test
        [clojure.pprint :only [cl-format]]
        [midje.util.object-utils :only [function-name function-name-or-spewage named-function?]]
        [midje.util.exceptions :only [friendly-exception-text]]
        [midje.checkers.util :only [captured-exception? captured-exception-value]]
        [midje.util.form-utils :only [pred-cond]])
  (:require [midje.util.colorize :as color]))

(let [the-var (intern (the-ns 'clojure.test) 'old-report)]
  (when-not (.hasRoot the-var)
    (.bindRoot the-var clojure.test/report)))

(def ^{:dynamic true :private true} *renderer* println)


;;; This mechanism is only used to make `fact` return appropriate values of
;;; true or false. It doesn't piggyback off clojure.test/*report-counters*
;;; partly because that's not normally initialized and partly to reduce
;;; dependencies.
(def ^{:dynamic true :private true} *failure-in-fact* false)
(defn note-failure-in-fact
  ([] (note-failure-in-fact true))
  ([val] (alter-var-root #'*failure-in-fact* (constantly val))))
(defn fact-begins []
  (note-failure-in-fact false))
(defn fact-checks-out? [] (not *failure-in-fact*))


(defn form-providing-friendly-return-value [test-form]
  `(do (fact-begins)
       ~test-form
       (fact-checks-out?)))

(defn midje-position-string [[filename line-num]]
  (format "(%s:%s)" filename line-num))

(defmacro with-identity-renderer [& forms]   ; for testing
  `(binding [*renderer* identity] ~@forms))

(defn- attractively-stringified-form [form]
  (pred-cond form
    named-function?     (format "a function named '%s'" (function-name form))  
    captured-exception? (friendly-exception-text (captured-exception-value form) "              ")
    :else               (pr-str form)))

(defn- fail-at [m]
  [(str "\n" (color/fail "FAIL") " at " (midje-position-string (:position m)))
   (when (:binding-note m)
     (str "With table substitutions: " (:binding-note m)))])

(defn- indented [lines]
  (map #(str "        " %) lines))

(defmulti report-strings :type)

(defmethod report-strings :mock-argument-match-failure [m]
   (list
    (fail-at m)
    (str "You never said "
         (function-name-or-spewage (:lhs m))
         " would be needed with these arguments:")
    (str "    " (pr-str (:actual m)))))

(defmethod report-strings :mock-incorrect-call-count [m]
   (list
    (fail-at m)
    (if (zero? (:actual-count m))
      "You claimed the following was needed, but it was never used:"
      (cl-format nil
                 "The following prerequisite was used ~R time~:P. That's not what you predicted."
                 (:actual-count m)))
    (str "    " (:expected m))))

(defmethod report-strings :mock-expected-result-failure [m]
   (list
    (fail-at m)
    (str "    Expected: " (pr-str (:expected m)))
    (str "      Actual: " (attractively-stringified-form (:actual m)))))

(defmethod report-strings :mock-expected-result-inappropriately-matched [m]
   (list
    (fail-at m)
    (str "    Expected: Anything BUT " (pr-str (:expected m)))
    (str "      Actual: " (attractively-stringified-form (:actual m)))))

(defmethod report-strings :mock-expected-result-functional-failure [m]
  (list
   (fail-at m)
   "Actual result did not agree with the checking function."
   (str "        Actual result: " (attractively-stringified-form (:actual m)))
   (str "    Checking function: " (pr-str (:expected m)))
   (if (:intermediate-results m)
     (cons "    During checking, these intermediate values were seen:"
           (for [[form value] (:intermediate-results m)] 
             (format "       %s => %s" (pr-str form) (pr-str value)))))
    (if (:notes m)
     (cons "    The checker said this about the reason:"
           (indented (:notes m))))))

(defmethod report-strings :mock-actual-inappropriately-matches-checker [m]
  (list
   (fail-at m)
   "Actual result was NOT supposed to agree with the checking function."
   (str "        Actual result: " (attractively-stringified-form (:actual m)))
   (str "    Checking function: " (pr-str (:expected m)))))

(defmethod report-strings :future-fact [m]
  (list
   (str "\n" (color/note "WORK TO DO:") " "
        (:description m)
        (midje-position-string (:position m)))))
  
(defmethod report-strings :user-error [m]
   (list
    (fail-at m)
    (str "    Midje could not understand something you wrote: ")
    (indented (:notes m))))
  
(defmethod report-strings :exceptional-user-error [m]
   (list
    (fail-at m)
    (str "    Midje caught an exception when translating this form:")
    (str "      " (pr-str (:macro-form m)))
    (str "      " "This stack trace *might* help:")
    (indented (:exception-lines m))))
  
(letfn [(render [m]
          (->> m report-strings flatten (remove nil?) (map *renderer*) doall))]

  (defmethod clojure.test/old-report :default [m]
    (inc-report-counter :fail )
    (note-failure-in-fact)
    (render m))

  (defmethod clojure.test/old-report :future-fact [m]
    (render m)))
