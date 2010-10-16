(ns jiraph.graph-test
  (:use clojure.test jiraph.graph)
  (:require [jiraph.byte-append-layer :as bal]
            [jiraph.tokyo-database :as tokyo]
            [jiraph.reader-append-format :as raf]
            [jiraph.protobuf-append-format :as paf])
  (:import [jiraph Test$Node]))

(def test-graph
  {:tr (bal/make (tokyo/make {:path "/tmp/jiraph-test-tokyo-reader"   :create true}) (raf/make))
   :tp (bal/make (tokyo/make {:path "/tmp/jiraph-test-tokyo-protobuf" :create true}) (paf/make Test$Node))})

(deftest single-layer
  (with-graph test-graph
    (doseq [layer (keys test-graph)]
      (truncate! layer)

      (testing "add-node! won't overwrite existing node"
        (let [node {:id "1" :foo 2 :bar "three"}]
          (is (= node (add-node! layer "1" node)))
          (is (= node (get-node layer "1")))
          (is (= nil  (add-node! layer "1" {:foo 8})))
          (is (= node (get-node layer "1")))))

      (testing "assoc-node! modifies specific attributes"
        (let [old {:id "1" :foo 2 :bar "three"}
              new {:id "1" :foo 54 :bar "three" :baz [1 2 3]}]
          (is (= [old new] (assoc-node! layer "1" {:foo 54 :baz [1 2 3]})))
          (is (= new (get-node layer "1")))))

      (testing "assoc-node! creates node if it doesn't exist"
        (let [node {:id "2" :foo 9 :bar "the answer"}]
          (is (= [nil node] (assoc-node! layer "2" {:foo 9 :bar "the answer"})))
          (is (= node (get-node layer "2")))))

      (testing "node-ids, node-count and node-exists?"
        (is (= #{"2" "1"} (set (node-ids layer))))
        (is (= 2 (node-count layer)))
        (doseq [id ["1" "2"]]
          (is (node-exists? layer id)))
        (doseq [id ["8" "9" "234"]]
          (is (not (node-exists? layer id)))))

      (testing "update-node! supports artitrary functions"
        (let [node1 {:id "1" :foo 54 :bar "three" :baz [1 2 3]}]
          (let [node2 {:id "1" :foo 54 :bar "three"}]
            (is (= [node1 node2] (update-node! layer "1" dissoc :baz)))
            (is (= node2 (get-node layer "1")))
            (let [node3 {:id "1" :foo 54 :bar "three" :baz [5]}]
              (is (= [node2 node3] (update-node! layer "1" assoc :baz [5])))
              (is (= node3 (get-node layer "1")))
              (let [node4 {:id "1" :foo 54 :baz [5]}]
                (is (= [node3 node4] (update-node! layer "1" select-keys [:foo :baz])))
                (is (= node4 (get-node layer "1"))))))))

      (testing "append-node! supports viewing old revisions"
        (let [node  {:id "3" :bar "cat" :baz [5] :rev 100}
              attrs {:id "3" :baz [8] :rev 101}]
          (at-revision 100
            (is (= node (append-node! layer "3" (dissoc node :rev))))
            (is (= node (get-node layer "3"))))
          (at-revision 101
            (is (= attrs (append-node! layer "3" (dissoc attrs :rev))))
            (is (= {:id "3" :bar "cat" :baz [5 8] :rev 101} (get-node layer "3"))))
          (at-revision 100
            (is (= node (get-node layer "3"))))))

      (testing "compact-node! removes revisions but leaves all-revisions"
        (let [old {:id "3" :bar "cat" :baz [5 8] :rev 101}
              new {:id "3" :bar "cat", :baz [5 8]}]
          (is (= '(100 101) (get-revisions layer "3")))
          (is (= '(100 101) (get-all-revisions layer "3")))
          (is (= [old new] (compact-node! layer "3")))
          (is (= () (get-revisions layer "3")))
          (is (= '(100, 101) (get-all-revisions layer "3")))))

      (testing "revisions and all-revisions returns an empty list for nodes without revisions"
        (is (= () (get-revisions layer "1")))
        (is (= () (get-all-revisions layer "1"))))

      (testing "keeps track of incoming edges"
        (is (= #{} (get-incoming layer "1")))
        (is (add-node! layer "4" {:edges {"1" {:a "one"}}}))
        (is (= #{"4"} (get-incoming layer "1")))
        (is (add-node! layer "5" {:edges {"1" {:b "two"}}}))
        (is (= #{"4" "5"} (get-incoming layer "1")))
        (is (append-node! layer "5" {:edges {"1" {:deleted true}}}))
        (is (= #{"4"} (get-incoming layer "1")))
        (is (assoc-node! layer "4" {:edges {"2" {:a "1"} "3" {:b "2"}}}))
        (is (empty? (get-incoming layer "1")))
        (is (= #{"4"} (get-incoming layer "2")))
        (is (= #{"4"} (get-incoming layer "3"))))

      (testing "transactions"
        (let [node {:id "7" :foo 7 :bar "seven"}]
          (with-transaction layer
            (is (= node (add-node! layer "7" node)))
            (is (= node (get-node layer "7")))
            (abort-transaction))
          (is (= nil (get-node layer "7")))
          (with-transaction layer
            (is (= node (add-node! layer "7" node))))
          (is (= node (get-node layer "7")))))

      (testing "layer-wide properties"
        (is (= nil (get-property layer :foo)))
        (is (= [1 2 3] (set-property! layer :foo [1 2 3])))
        (is (= [1 2 3] (get-property layer :foo)))
        (is (= [1 2 3 5] (update-property! layer :foo conj 5)))
        (is (= [1 2 3 5] (get-property layer :foo))))

      (testing ":rev property stores max committed revision"
        (at-revision 102
          (with-transaction layer
            (add-node! layer "8" {:foo 8}))
          (is (= 8 (:foo (get-node layer "8"))))
          (is (= 102 (get-property layer :rev)))))

      (testing "past revisions are ignored inside of transactions"
        (at-revision 101
          (with-transaction layer
            (add-node! layer "8" {:foo 9}))
          (is (= 8 (:foo (get-node layer "8")))))))))