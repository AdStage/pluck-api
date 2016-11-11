(remove-ns 'adstage.pluck-api-test)
(ns adstage.pluck-api-test
  (:require [adstage.pluck-api :as p :refer [defmethod-cached]]
            [clojure.test :refer :all]))

(defmethod p/-pluck :data-source/blob [k {store :blob-store} {eid :db/id}]
  (store eid))

(defmethod p/-pluck-many :dashboard/created-at [k env results]
  (let [ids (map :db/id results)]
    (map (fn [id]
           [id #inst "2016-11-10T22:36:38.210-00:00"]) ids)))

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

  (testing "Shallow pluck-many with extension."
    (let [query        [:db/id :dashboard/title :dashboard/created-at]
          init-resutls [{:db/id           17592186057566
                         :dashboard/title "dash1"}
                        {:db/id           17592186088888
                         :dashboard/title "dash2"}]]
      (is (= [{:db/id                17592186057566
               :dashboard/title      "dash1"
               :dashboard/created-at #inst "2016-11-10T22:36:38.210-00:00"}
              {:db/id                17592186088888
               :dashboard/title      "dash2"
               :dashboard/created-at #inst "2016-11-10T22:36:38.210-00:00"}]
             (p/pluck-many {} query init-resutls)))))

  (testing "Nested pluck-many cardinality one."
    (let [query        [:db/id :dashboard/title {:dashboard/author [:db/id :user/first-name]}]
          init-resutls [{:db/id            1
                         :dashboard/title  "Such Dashing Wow!!!"
                         :dashboard/author {:db/id 2 :user/first-name "Clark"}}
                        {:db/id            4
                         :dashboard/title  "Such barking Wow!!!"
                         :dashboard/author {:db/id 5 :user/first-name "Bark"}}]]
      (is (= [{:db/id            1 :dashboard/title "Such Dashing Wow!!!"
               :dashboard/author {:db/id 2 :user/first-name "Clark"}}
              {:db/id            4 :dashboard/title "Such barking Wow!!!"
               :dashboard/author {:db/id 5 :user/first-name "Bark"}}]
             (p/pluck-many {} query init-resutls)))))
  )
