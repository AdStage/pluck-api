# pluck-api
An small extensible wrapper around Datomic's pull API using multimethods.

[![Clojars Project](https://img.shields.io/clojars/v/adstage/pluck-api.svg)](https://clojars.org/adstage/pluck-api)

# Usage

Datomic's pull API offers a powerfully declarative way to access data
stored directly in Datomic. However sometimes we need to access the data
in a particular way that is not directly represented in the schema. In this
cases the pull API is not sufficient. `datomic.api/q` does the job
but it is not ideal for things like client-side co-located queries in Om.Next.

With the `pluck-api` we retain the power of client-side queries by extending
the pull API with multimethods. It also gives you a clean way to consistently
organize your datomic queries.

The API is pretty simple. The arguments to `adstage.pluck-api/pluck` are
- `env` a map with stuff like a Datomic db, foreign blob store etc.
- `pattern` pull API pattern with extra keys used by the pluck API.
- `eid-or-map` if Datomic entity id then `datomic.api/pull` is used to get the intial result, or you can pass in a map as the initial result.

The API is extended via implemting the `adstage.pluck-helper/-pluck` multimethod.

The API also has a `adstage.pluck-api/pluck-many` function and `adstage.pluck-helper/-pluck-many`
multimethod. These are analogous to the regular pluck function but wrap around `pluck-many`. They
have slightly different requirements, detailed below.

## Examples

### Datalog Query

```clojure
(require '[adstage.pluck-api :refer [defmethod-cached -pluck pluck -pluck-many pluck-many]])
```

Say we want to query some data from a dashboard that is stored in the `:db/txInstant`
datom.

```clojure
(defmethod -pluck :dashboard/org-time [k {db :db} {eid :db/id}]
  (d/q '[:find  ?created-at .
         :in    $ ?d
         :where [?d :dashboard/organization _ ?org-tx _]
                [?org-tx :db/txInstant ?created-at]]
       db
       eid))
       
(pluck {:db db} [:dashboard/title :dashboard/org-time] dash-eid)
;=> {:dashboard/title "FooBar" :dashboard/org-time #inst "2016-08-20T22:10:26.652-00:00"}

(defmethod -pluck-many :dashboard/org-time [k {db :db} dashboards]
  (->> (d/q '[:find  ?d ?created-at
              :in    $ [?d ...]
              :where [?d :dashboard/organization _ ?org-tx _] 
                     [?org-tx :db/txInstant ?created-at]]
            db
            (map :db/id dashboards))
       (into [])))
       
(pluck-many {:db db} [:db/id :dashboard/title :dashboard/org-time] [d1 d2])
;=> [{:db/id d1 :dashboard/title "FooBar" :dashboard/org-time #inst "2016-08-20T22:10:26.652-00:00"}
;=>  {:db/id d2 :dashboard/title "BarFoo" :dashboard/org-time #inst "2016-08-21T22:10:26.652-00:00"}]
```

### External Blob Store

Datomic is not built for storing large blobs of data, so we often use an external blob store.
This also poses a problem for the pull API but we can get around that like so.

```clojure
(defmethod -pluck :data-source/current-stream
  [k {:keys [db blob-store]} {ds-eid :db/id}]
  (let [data-stream     (-> (d/entity db ds-eid) :data-source/current-stream)
        uuid            (:data-stream/id data-stream)]
    (data-stream/find-by-id blob-store uuid)))
    
(pluck {:db db :blob-store blob-store} [:db/id :data-source/current-stream] ds-eid)
;=> {:db/id ds-eid :data-source/current-stream large-blob}

(defmethod -pluck-many :data-source/current-stream
  [k {:keys [db blob-store]} data-sources]
  (let [data-streams     (map #(-> (d/entity db %) :data-source/current-stream)
                              data-sources)
        id-tuples        (map (fn [{eid :db/id id :data-stream/id}]
                                [eid id])
                              data-stream)]
    ;; Returns tuples [eid blob-value]
    (data-stream/find-by-ids blob-store id-tuples)))
    
(pluck-many {:db db :blob-store blob-store} [:db/id :data-source/current-stream] [ds1 ds2])
;=> [{:db/id ds1 :data-source/current-stream large-blob}
;=>  {:db/id ds2 :data-source/current-stream other-large-blob}]
```

### Caching

We also have a simple macro that will only execute its body if the target key is not passed into
the initial result. Useful for when you are querying over a collection of entities and want to pre-fetch
some data with a single call to `datomic.api/q`.

```clojure
(defmethod-cached -pluck :data-source/fields [k {db :db} pull-result]
  (clojure.edn/read-string (get pull-result :data-source/fields-str)))
  
;; This will not execute the code above.
(pluck {:db db} [:data-source/fields] {:data-source/fields {:foo :bar}})
;=> {:data-source/fields {:foo :bar}}
```

# License

Copyright © 2016 AdStage
Distributed under the MIT License (MIT)
