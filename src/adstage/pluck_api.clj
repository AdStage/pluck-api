(ns adstage.pluck-api
  (:require
   [adstage.pluck-helper :refer [-pluck]]
   [datomic.api :as d]))

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
                 (keyword? k) (-pluck k env init-result)

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
