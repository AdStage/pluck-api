(ns adstage.pluck-api-test
  (:require [adstage.pluck-api :as p]
            [adstage.pluck-helper :refer [defmethod-cached] :as h]
            [clojure.test :refer :all]))

(defmethod h/-pluck :data-source/blob [k {store :blob-store} {eid :db/id}]
  (store eid))

(defmethod-cached h/-pluck :dashboard/refreshed-at [k env init-result]
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
                            (p/pluck {} query {}))))))
