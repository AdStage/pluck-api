(ns adstage.pluck-helper)

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
