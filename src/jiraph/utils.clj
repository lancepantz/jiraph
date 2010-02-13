(ns jiraph.utils
  (:use clojure.contrib.math))

(defn tap [f obj]
  (f obj)
  obj)

(defn args-map [args] ; based on cupboard.utils
  (cond
    (map? args) args
    (and (sequential? args) (= (count args) 1)) (args-map (first args))
    :else (apply hash-map args)))

(defn assoc-or [map key value]
  (if (map key)
    map
    (assoc map key value)))

(defn assoc-if [map test & args]
  (if test
    (apply assoc map args)
    map))

(defmacro verify [x exception & body]
  `(if ~x
     (do ~@body)
     (throw (if (string? ~exception)
              (Exception. ~exception)
              ~exception))))

(defn find-index [pred vec]
  (let [n (count vec)]
    (loop [i 0]
      (when-not (= n i)
        (if (pred (nth vec i))
          i
          (recur (inc i)))))))

(defn remove-nth [vec index]
  (concat (subvec vec 0 index) (subvec vec (inc index) (count vec))))

(defmacro let-if [test bindings & body]
  `(if ~test
     (let ~bindings
       ~@body)
     ~@body))

(defmacro defclass [class & fields]
  `(let [type#   (keyword (name (quote ~class)))
         struct# (create-struct ~@fields)]
     (defn ~class [& args#]
       (let [instance# (if (= (count args#) 1)
                         (let [attrs# (first args#)]
                           (cond (map? attrs#)    (merge (struct-map struct#) attrs#)
                                 (vector? attrs#) (apply struct struct# attrs#)
                                 (seq?    attrs#) (apply struct-map struct# attrs#)
                                 :else (throw (IllegalArgumentException. "single arg must be map or vector"))))
                         (apply struct-map struct# args#))]
         (with-meta instance# {:type type#})))))

(defn take-rand [vec]
  (let [i (rand-int (count vec))]
    (vec i)))

(defn queue
  ([]    clojure.lang.PersistentQueue/EMPTY)
  ([seq] (if (sequential? seq)
           (into (queue) seq)
           (conj (queue) seq))))

(defn slice [n coll]
  (loop [num    n
         slices []
         items  (vec coll)]
    (if (empty? items)
      slices
      (let [size (ceil (/ (count items) num))]
        (recur (dec num) (conj slices (subvec items 0 size)) (subvec items size))))))

(defn pcollect [f coll]
  (let [n    (.. Runtime getRuntime availableProcessors)
        rets (map #(future (map f %)) (slice n coll))]
    (mapcat #(deref %) rets)))

(defn assoc-in!
  "Associates a value in a nested associative structure, where ks is a sequence of keys
  and v is the new value and returns a new nested structure. The associative structure
  can have transients in it, but if any levels do not exist, non-transient hash-maps will
  be created."
  [m [k & ks :as keys] v]
  (let [assoc (if (instance? clojure.lang.ITransientCollection m) assoc! assoc)]
    (if ks
      (assoc m k (assoc-in! (get m k) ks v))
      (assoc m k v))))

(defn update-in!
  "'Updates' a value in a nested associative structure, where ks is a sequence of keys and
  f is a function that will take the old value and any supplied args and return the new
  value, and returns a new nested structure. The associative structure can have transients
  in it, but if any levels do not exist, non-transient hash-maps will be created."
  [m [k & ks] f & args]
  (let [assoc (if (instance? clojure.lang.ITransientCollection m) assoc! assoc)]
    (if ks
      (assoc m k (apply update-in! (get m k) ks f args))
      (assoc m k (apply f (get m k) args)))))