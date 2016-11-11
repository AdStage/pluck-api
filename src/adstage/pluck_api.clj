(ns adstage.pluck-api
  (:require
   [datomic.api :as d]
   [clojure.core.reducers :as r]))

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
       (map (fn [k]
              [(cond
                 (keyword? k) k
                 (map? k)     (-> k keys first))
               (cond
                 (keyword? k) (or (k init-result)
                                  (-pluck k env init-result))

                 (and (map? k)
                      (vector? (get init-result (-> k keys first))))
                 (mapv #(pick env (-> k vals first) %) (get init-result (-> k keys first)))

                 (and (map? k)
                      (get init-result (-> k keys first)))
                 (pick env (-> k vals first) (get init-result (-> k keys first)))

                 (and (map? k)
                      (nil? (get init-result (-> k keys first))))
                 [])]))
       (into {})))

(defn pluck
  "An extensible wrapper around `datomic.api/pull`. Takes an environment as the
  first argument. Second argument is a pull style query. Third argument is an
  entity id or entity map (from a previous pull/pluck call). Specific key lookup
  is implemented via `-pluck` multimethod. Default case is `datomic.api/pull`."
  [{:keys [db] :as env} pattern eid-or-map]
  (let [init-result (if (map? eid-or-map)
                      eid-or-map
                      (d/pull db pattern eid-or-map))]
    (pick env pattern init-result)))

(defmulti -pluck-many
  "Multimethod for extending datomic.api/pull-many. The return
  value must be a collection of tuples.
  `[entity-id pluck-value]`"
  (fn [k env init-results] k))

(defn -pluck-many? [k]
  (contains? (methods -pluck-many) k))

(defn -pluck-many-wrapper [k env init-results]
  (mapv (fn [[eid v]]
          {eid {k v}})
        (-pluck-many k env init-results)))

(defn pick-many
  "Recursively annotates the init-result with values supplied from the
  `-pluck-many` multimethod for root level keys and `-pluck` for sub queries."
  [env pattern init-results]
  (let [grouped-results (->> (group-by :db/id init-results)
                             (map (fn [[k ms]] [k (first ms)]))
                             (into {}))
        plucked-results (->> pattern
                             (mapcat (fn [k]
                                       (cond
                                         (keyword? k) (if (-pluck-many? k)
                                                        (-pluck-many-wrapper k env init-results))

                                         (and (map? k)
                                              (some identity (map #(get % (-> k keys first)) init-results)))
                                         (let [sub-default-results (map (fn [{eid :db/id :as ir}]
                                                                          [eid (get ir (-> k keys first))]) init-results)
                                               sub-query           (-> k vals first)
                                               sub-results         (mapv (fn [[eid sub-init-result]]
                                                                           {eid {(-> k keys first)
                                                                                 (pick env sub-query sub-init-result)}})
                                                                         sub-default-results)]
                                           sub-results))))
                             (filter identity))

        merged-results (apply merge-with merge grouped-results plucked-results)]
    (mapv (fn [[k v]] v) merged-results)))

(defn pluck-many
  "An extensible wrapper around `datomic.api/pull-many`. First argument is an
  environment as the first argument.

  Second argument is a pull style query. The root entity must have :db/id in its
  query.

  Third argument is a collection of entity ids or entity maps.

  The default key lookup `datomic.api/pull-many`. But it can be overridden by
  implementing `-pluck-many` multimethod. Not this multimethod is only used for
  root level keys. Nested queries are processed with `-pluck` multimethod."
  [{:keys [db] :as env} pattern eids-or-maps]
  (let [init-results (if (every? map? eids-or-maps)
                       eids-or-maps
                       (d/pull-many db pattern eids-or-maps))]
    (pick-many env pattern init-results)))
