(ns adstage.pluck-api-test
  (:require [adstage.pluck-api :as p :refer [defmethod-cached inspect]]
            [clojure.test :refer :all]))

(defmethod p/-pluck :data-source/blob [k {store :blob-store} {eid :db/id}]
  (store eid))

(defmethod-cached p/-pluck :dashboard/refreshed-at [k env init-result]
  (throw
   (Exception. "Should never get here if :dashboard/refreshed-at is passed in.")))

(deftest pluck-api-test
  (testing "Basic nested Datomic pull result traversal, no pluck extension."
    (let [query       [:dashboard/title
                       {:dashboard/widgets
                        [:db/id :widget/title
                         {:widget/data-source [:data-source/owner]}]}
                       {:dashboard/author [:user/first-name]}]
          init-result {:dashboard/title  "Such Dashing Wow!!!"
                       :dashboard/widgets
                       [{:db/id              17592186057566
                         :widget/title       "Unnamed"
                         :widget/data-source {:data-source/owner {:db/id 17592186045435}}}
                        {:db/id              17592186057638
                         :widget/title       "Metered Metrics"
                         :widget/data-source {:data-source/owner {:db/id 17592186045435}}}]
                       :dashboard/author {:user/first-name "Clark"}}]
      (is (= init-result
             (p/pluck {} query init-result)))))

  (testing "Extending with a foreign blob store."
    (let [query       [{:dashboard/widgets [{:widget/data-source [:db/id :data-source/blob]}]}]
          init-result {:dashboard/widgets
                       [{:widget/data-source {:db/id :db/id-1}}
                        {:widget/data-source {:db/id :db/id-2}}]}

          blob-store {:db/id-1 "first-blob"
                      :db/id-2 "second-blob"}]
      (is (= {:dashboard/widgets
              [{:widget/data-source
                {:db/id :db/id-1 :data-source/blob "first-blob"}}
               {:widget/data-source
                {:db/id :db/id-2 :data-source/blob "second-blob"}}]}
             (p/pluck {:blob-store blob-store} query init-result)))))

  (testing "Cached extension should only be called if key missing from init-result."
    (let [query       [:dashboard/refreshed-at]
          init-result {:dashboard/refreshed-at #inst "2016-08-20T22:10:26.652-00:00"}]
      (is (= {:dashboard/refreshed-at #inst "2016-08-20T22:10:26.652-00:00"}
             (p/pluck {} query init-result)))
      (is (thrown-with-msg? Exception #"dashboard\/refreshed-at"
                            (p/pluck {} query {})))))

  (testing "Queies with * work"
    (let [query '[*
                  {:widget/layout [:layout/width]} ; Cardinality one specific key join.
                  {:widget/data-source ; Cardinality one combination join.
                   [*
                    {:data-source/current-stream [*]} ; Cardinality one * join.
                    {:data-source/data-streams [*]}]}] ; Cardinality many * join.

          pull-result {:db/id         17592186057341,
                       :widget/title  "Week Over Week Performance Summary",
                       :widget/data-source
                       {:data-source/current-stream
                        {:db/id              17592186057795,
                         :data-stream/id     #uuid "5831f985-3208-4df6-a6c9-46046d11e9dd",
                         :data-stream/status {:db/id 17592186054609}},
                        :data-source/compare-to    "previous_period",
                        :data-source/data-streams
                        [{:db/id              17592186057343,
                          :data-stream/id     #uuid "57f7d9de-834e-44cd-b510-a9b0fbfd2068",
                          :data-stream/status {:db/id 17592186045433}}
                         {:db/id              17592186057371,
                          :data-stream/id     #uuid "5812d021-76ed-44ee-b58e-c98670f34e52",
                          :data-stream/status {:db/id 17592186054609}}
                         {:db/id              17592186057415,
                          :data-stream/id     #uuid "58150b34-a973-48f9-8d5c-06cdf9c4184f",
                          :data-stream/status {:db/id 17592186054609}}
                         {:db/id              17592186057431,
                          :data-stream/id     #uuid "58150b6c-8bef-4892-9c4f-defff441d735",
                          :data-stream/status {:db/id 17592186045433}}
                         {:db/id              17592186057795,
                          :data-stream/id     #uuid "5831f985-3208-4df6-a6c9-46046d11e9dd",
                          :data-stream/status {:db/id 17592186054609}}],
                        :data-source/response-type {:db/id 17592186045428},
                        :data-source/limit         10,
                        :data-source/status        {:db/id 17592186054331},
                        :data-source/fields-str    "[\"spend\" \"clicks\" \"ctr\"]",
                        :db/id                     17592186057344,
                        :data-source/order         {:db/id 17592186045431},
                        :data-source/id            #uuid "57f7d9de-0734-4888-90fd-f07094e58c7a",
                        :data-source/list-name     "network_campaigns",
                        :data-source/sort-by       "clicks",
                        :data-source/networks      ["adwords" "bing_ads" "facebook" "linkedin" "twitter"],
                        :data-source/owner         {:db/id 17592186045435},
                        :data-source/filters-str   "[]",
                        :data-source/target        "/api/users/40",
                        :data-source/provider      {:db/id 17592186045429},
                        :data-source/timeframe     "last_1_week"},
                       :widget/type   {:db/id 17592186055013},
                       :widget/layout {:layout/width "12"}}]
      (is (= pull-result (p/pluck {} query pull-result))))))
