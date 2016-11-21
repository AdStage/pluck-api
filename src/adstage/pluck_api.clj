(ns adstage.pluck-api
  (:require [datomic.api :as d]))

(defn- inspect-1 [expr]
  `(let [result# ~expr]
     (println)
     (print (str (pr-str '~expr) " => "))
     (clojure.pprint/pprint result#)
     result#))

(defmacro inspect [& exprs]
  `(do ~@(map inspect-1 exprs)))

(defmulti -pluck
  "Multimethod for extending datomic.api/pull, while still maintaining the ability
  to let the query decied what is actually fetched."
  (fn [k env init-result] k))

;; Default case just delivers what datomic.api/pull gives you.
(defmethod -pluck :default [k env init-result]
  (get init-result k))

(defmacro defmethod-cached
  "Wrap the -pluck multimethod with some basic caching based on its
  third argument, the accumulated init-result."
  [title dispatch-key args & body]
  (assert (= 3 (count args)) "Only three arguments allowed.")
  (assert (= "-pluck" (name title)) "Only defined for -pluck multimethod.")
  `(defmethod ~title ~dispatch-key [k# env# init-result#]
     (if-let [result# (~dispatch-key init-result#)]
       result#
       ((fn ~args ~@body)
        k# env# init-result#))))

(defn pick
  "Recursively annotates the init-result with values supplied from the `-pluck`
  multimethod."
  [env pattern init-result]
  (->> pattern
       (mapcat (fn [k]
                 (if (or (= '* k)
                         (and (map? k) (= '... (-> k vals first))))
                   (into [] init-result)
                   [[(cond
                       (keyword? k) k
                       (map? k)     (-> k keys first))
                     (cond
                       (keyword? k) (-pluck k env init-result)

                       (and (map? k)
                            (vector? (get init-result (-> k keys first))))
                       (mapv #(pick env (-> k vals first) %) (get init-result (-> k keys first)))

                       (and (map? k)
                            (get init-result (-> k keys first)))
                       (pick env (-> k vals first) (get init-result (-> k keys first)))

                       (and (map? k)
                            (nil? (get init-result (-> k keys first))))
                       [])]])))
       (into {})))

(defn pluck
  "A more generic version of the datomic.api/pull. Takes an environment
  as the first argument.
  Second argument is a pull style query. Third argument is an entity id or
  entity map (from a previous pull/pluck call).
  Specific key lookup is implemented via -pluck multimethod. Default case is
  datomic.api/pull."
  [{:keys [db] :as env} pattern eid-or-map]
  (let [init-result (if (map? eid-or-map)
                      eid-or-map
                      (d/pull db pattern eid-or-map))]
    (pick env pattern init-result)))
