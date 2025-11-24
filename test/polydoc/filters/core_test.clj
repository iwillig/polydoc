(ns polydoc.filters.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [polydoc.filters.core :as core]))

(deftest test-ast-node?
  (testing "Identifies AST nodes"
    (is (true? (core/ast-node? {:t "Para" :c []})))
    (is (false? (core/ast-node? {})))
    (is (false? (core/ast-node? {:c []})))))

(deftest test-node-type
  (testing "Extracts node type"
    (is (= "Para" (core/node-type {:t "Para" :c []})))))

(deftest test-node-content
  (testing "Extracts node content"
    (is (= [1 2 3] (core/node-content {:t "Para" :c [1 2 3]})))))

(deftest test-make-node
  (testing "Creates AST nodes"
    (is (= {:t "Para" :c ["content"]}
           (core/make-node "Para" ["content"])))))

(deftest test-walk-ast
  (testing "Walks AST and transforms nodes"
    (let [ast {:pandoc-api-version [1 23]
               :meta {}
               :blocks [{:t "Para" :c ["Hello"]}
                        {:t "CodeBlock" :c [[] "code"]}]}
          result (core/walk-ast
                  (fn [node]
                    (if (= (core/node-type node) "Para")
                      (core/make-node "Para" ["Modified"])
                      node))
                  ast)]
      (is (= "Modified" (-> result :blocks first :c first))))))

(deftest test-filter-nodes
  (testing "Filters nodes by type"
    (let [ast {:blocks [{:t "Para" :c ["p1"]}
                        {:t "CodeBlock" :c [[] "code"]}
                        {:t "Para" :c ["p2"]}]}
          paras (core/filter-nodes ast "Para")]
      (is (= 2 (count paras)))
      (is (every? #(= "Para" (:t %)) paras)))))

(deftest test-compose-filters
  (testing "Composes multiple filters"
    (let [add-one (fn [ast] (update ast :value inc))
          double-it (fn [ast] (update ast :value * 2))
          composed (core/compose-filters add-one double-it)
          result (composed {:value 5})]
      (is (= 12 (:value result))))))

(deftest test-safe-filter
  (testing "Handles filter errors gracefully"
    (let [broken-filter (fn [_] (throw (Exception. "Boom!")))
          safe (core/safe-filter broken-filter)
          ast {:blocks []}]
      (is (= ast (safe ast))))))
