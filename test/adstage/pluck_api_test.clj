(ns adstage.pluck-api-test
  (:require [adstage.pluck-api :as p :refer [defmethod-cached inspect]]
            [clojure.test :refer :all]))

(defmethod p/-pluck :data-source/blob [k {store :blob-store} {eid :db/id}]
  (store eid))

(defmethod-cached p/-pluck :dashboard/refreshed-at [k env init-result]
  (throw
   (Exception. "Should never get here if :dashboard/refreshed-at is passed in.")))

(defmethod p/-pluck :dashboard/created-at [k env init-result]
  #inst "2016-11-21T21:10:09.585-00:00")

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
    (let [query       '[* :dashboard/title
                        :dashboard/created-at
                        {:dashboard/widgets [{:widget/data-source [:db/id :data-source/blob]}]}]
          init-result {:dashboard/title "foobar"
                       :dashboard/widgets
                       [{:widget/data-source {:db/id :db/id-1}}
                        {:widget/data-source {:db/id :db/id-2}}]}

          blob-store {:db/id-1 "first-blob"
                      :db/id-2 "second-blob"}]
      (is (= {:dashboard/title      "foobar"
              :dashboard/created-at #inst "2016-11-21T21:10:09.585-00:00"
              :dashboard/widgets
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
                  ;; Cardinality one specific key join.
                  {:widget/layout [:layout/width]}
                  ;; Cardinality one combination join.
                  {:widget/data-source 
                   [*
                    ;; Cardinality one * join.
                    {:data-source/current-stream [*]}
                    ;; Cardinality many * join.
                    {:data-source/data-streams [*]}]}]

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
      (is (= pull-result (p/pluck {} query pull-result)))))

  (testing "Queies with ... work"
    (let [query '[:organization/company-name
                  {:organization/users [:db/id {:organization/_users ...}]}]

          pull-result {:organization/company-name "AdStage"
                       :organization/users
                       [{:db/id               17592186045506
                         :organization/_users [{:db/id 17592186045507}
                                               {:organization/company-name "RadStage"
                                                :organization/users
                                                [{:db/id 17592186045506 :organization/_users [{:db/id 17592186045507}]}
                                                 {:db/id 17592186045733 :organization/_users [{:db/id 17592186045507}]}]}]}
                        {:db/id 17592186045733 :organization/_users [{:db/id 17592186045507}]}]}]
      (is (= pull-result (p/pluck {} query pull-result)))))

  (testing "Combination complicated query"
    (let [query '[*
                  {:user/access-rights
                   [*
                    {:access-right/access
                     [* {:access/user-files
                         [* {:user-file/format        [*]
                             :user-file/file-meta     [* {:file-meta/root ...
                                                          :file-meta/body [*]}]
                             :user-file/file-versions [* {:file-versions/file-metas [* {:file-meta/root ...
                                                                                        :file-meta/body [*]}]
                                                          :file-versions/body       [*]
                                                          :file-versions/format     [*]}]
                             :user-file/user-content  [*]}]}
                      {:access/user-file-groups [* {:user-file-group/user-files
                                                    [* {:user-file/format       [*]
                                                        :user-file/file-meta
                                                        [* {:file-meta/root ...
                                                            :file-meta/body [*]}]
                                                        :user-file/file-versions
                                                        [* {:file-versions/file-metas [* {:file-meta/root ...
                                                                                          :file-meta/body [*]}]
                                                            :file-versions/body       [*]
                                                            :file-versions/format     [*]}]
                                                        :user-file/user-content [*]}]}]}]}]}
                  {:user/user-token-providers [*]}]


          pull-result {:db/id           277076930208294,
                       :user/id         #uuid "574f7e35-bae6-4b41-8030-e2cd34562922",
                       :user/email      "foobar",
                       :user/first-name "foobar",
                       :user/last-name  "foobar",
                       :user/access-rights
                       [{:db/id             277076930208295,
                         :access-right/id   #uuid "5807f09b-5472-4d3d-a3cd-c9053695ce80",
                         :access-right/role :default,
                         :access-right/access
                         {:db/id                 277076930208297,
                          :access/id             #uuid "574f7e5c-9e0b-41b9-88f7-e69b3729f16e",
                          :access/address-line-1 "foobar",
                          :access/user-file-groups
                          [{:db/id                      277076930208298,
                            :user-file-group/id
                            #uuid "5727d322-5649-4bf7-9e08-e02a31c46812",
                            :user-file-group/sort-order 1,
                            :user-file-group/name       "foobar",
                            :user-file-group/user-files
                            [{:user-file/acquisition-month 10,
                              :user-file/id                #uuid "5727d322-c2d2-4243-999e-3dfcbb14139d",
                              :user-file/file-meta
                              {:db/id          277076930207503,
                               :file-meta/id   #uuid "5727d2de-6a11-487c-b598-bc50d93ca4ec",
                               :file-meta/name "foobar",
                               :file-meta/root
                               {:db/id          277076930207488,
                                :file-meta/id   #uuid "5727d2de-452e-4a41-9235-f1f63b501565",
                                :file-meta/name "foobar"}},
                              :user-file/format
                              {:db/id       277076930207550,
                               :format/id   #uuid "5727d2de-6c83-4e01-bdd9-239933dfa31b",
                               :format/name "foobar"},
                              :user-file/model-number      "foobar",
                              :user-file/sort-order        0,
                              :user-file/serial-number     "foobar",
                              :db/id                       277076930208299,
                              :user-file/acquisition-year  2007,
                              :user-file/file-versions
                              {:db/id                       277076930208173,
                               :file-versions/id            #uuid "5727d2de-9e24-4ea9-a338-c46c3b989128",
                               :file-versions/model-number  "foobar",
                               :file-versions/format
                               {:db/id       277076930207550,
                                :format/id
                                #uuid "5727d2de-6c83-4e01-bdd9-239933dfa31b",
                                :format/name "foobar"},
                               :file-versions/body
                               [{:db/id              277076930207703
                                 ,
                                 :body/id
                                 #uuid "5727d2de-cbd8-4cd3-a248-0b51a47fbc99",
                                 :body/semantic-type :photograph,
                                 :body/body-binaries
                                 [{:db/id                    277076930207704,
                                   :body-binary/id
                                   #uuid "5727d2de-6562-471e-a1e3-2ea15d84d194",
                                   :body-binary/purpose      :original,
                                   :body-binary/content-type "foobar",
                                   :body-binary/remote-file
                                   "597b61e5f6a1dc676ff0f8068ef80536f5129e25.jpg",
                                   :body-binary/size         9303}]}],
                               :file-versions/primary-image {:db/id 277076930207703},
                               :file-versions/file-metas
                               [{:db/id          277076930207503,
                                 :file-meta/id   #uuid "5727d2de-6a11-487c-b598-bc50d93ca4ec",
                                 :file-meta/name "foobar",
                                 :file-meta/root
                                 {:db/id        277076930207488,
                                  :file-meta/id #uuid "5727d2de-452e-4a41-9235-f1f63b501565",
                                  :file-meta/name
                                  "foobar"}}]},
                              :user-file/nickname          "foobar"}
                             {:db/id                   277076930208300,
                              :user-file/id            #uuid "58193196-b519-4658-a678-4f6d28ba12a1",
                              :user-file/nickname      "foobar",
                              :user-file/serial-number "foobar",
                              :user-file/file-versions
                              {:db/id                      277076930208234,
                               :file-versions/id           #uuid "5807b64a-986d-488c-ba45-8ee8383b1c2a",
                               :file-versions/model-number "foobar",
                               :file-versions/format
                               {:db/id       277076930207624,
                                :format/id
                                #uuid "5807b679-d8c1-4a5b-8e25-10e8e5a82b24",
                                :format/name "foobar"},
                               :file-versions/file-metas
                               [{:db/id          277076930207498,
                                 :file-meta/id   #uuid "5727d2de-53a7-42be-afbf-55ca42f1a7d9",
                                 :file-meta/name "foobar",
                                 :file-meta/root
                                 {:db/id          277076930207493,
                                  :file-meta/id   #uuid "5727d2de-c333-410c-8932-ddb892ed191a",
                                  :file-meta/name "foobar"}}]}}
                             
                             {:db/id                   277076930208301,
                              :user-file/id            #uuid "581a35a5-5cf9-4cc3-b8dc-6c7d492267ad",
                              :user-file/nickname      "foobar",
                              :user-file/serial-number "foobar",
                              :user-file/file-versions
                              {:db/id                      277076930208233,
                               :file-versions/id           #uuid "5807b32a-7cda-4204-9417-9b4562b97274",
                               :file-versions/model-number "foobar",
                               :file-versions/format
                               {:db/id       277076930207609,
                                :format/id
                                #uuid "5727d2de-9dcd-46e0-82c7-54bb07eceb78",
                                :format/name "foobar"},
                               :file-versions/file-metas
                               [{:db/id          277076930207501,
                                 :file-meta/id   #uuid "5727d2de-38dd-426c-8ddb-b0f93f246815",
                                 :file-meta/name "foobar",
                                 :file-meta/root
                                 {:db/id          277076930207488,
                                  :file-meta/id   #uuid "5727d2de-452e-4a41-9235-f1f63b501565",
                                  :file-meta/name "foobar"},
                                 :file-meta/body
                                 [{:db/id 277076930208634,
                                   :body/id

                                   #uuid "58215743-93e0-4abb-adf7-c69483b89923",
                                   :body/semantic-type :nameplate-help-photo,
                                   :body/title         "foobar",
                                   :body/description   "foobar",
                                   :body/body-binaries
                                   [{:db/id                   277076930208635,
                                     :body-binary/id
                                     #uuid "58215743-f86f-4e4d-874a-0c1448bfcf81",
                                     :body-binary/purpose     :original,
                                     :body-binary/remote-file "foobar"}],
                                   :body/sort-order    0}
                                  {:db/id              277076930208637,
                                   :body/id            #uuid "58215743-466f-45e8-b5b5-38ba2ebae686",
                                   :body/semantic-type :nameplate-help-video,
                                   :body/title         "foobar",
                                   :body/description   "foobar",
                                   :body/link          "foobar",
                                   :body/sort-order    1}]}]}}]}]}}
                        {:db/id             277076930208296,
                         :access-right/id   #uuid "574f7e54-e765-4d3f-a596-9aef8b0f6f45",
                         :access-right/role :sponsor,
                         :access-right/access
                         {:db/id                 277076930208297,
                          :access/id             #uuid "574f7e5c-9e0b-41b9-88f7-e69b3729f16e",
                          :access/address-line-1 "foobar",
                          :access/user-file-groups
                          [{:db/id                      277076930208298,
                            :user-file-group/id
                            #uuid "5727d322-5649-4bf7-9e08-e02a31c46812",
                            :user-file-group/sort-order 1,
                            :user-file-group/name       "foobar",
                            :user-file-group/user-files
                            [{:user-file/acquisition-month
                              10,
                              :user-file/id               #uuid "5727d322-c2d2-4243-999e-3dfcbb14139d",
                              :user-file/file-meta
                              {:db/id          277076930207503,
                               :file-meta/id   #uuid "5727d2de-6a11-487c-b598-bc50d93ca4ec",
                               :file-meta/name "foobar",
                               :file-meta/root
                               {:db/id          277076930207488,
                                :file-meta/id   #uuid "5727d2de-452e-4a41-9235-f1f63b501565",
                                :file-meta/name "foobar"}},
                              :user-file/format
                              {:db/id       277076930207550,
                               :format/id   #uuid "5727d2de-6c83-4e01-bdd9-239933dfa31b",
                               :format/name "foobar"},
                              :user-file/model-number     "foobar",
                              :user-file/sort-order       0,
                              :user-file/serial-number    "foobar",
                              :db/id                      277076930208299,
                              :user-file/acquisition-year 2007,
                              :user-file/file-versions
                              {:db/id                      277076930208173,
                               :file-versions/id           #uuid "5727d2de-9e24-4ea9-a338-c46c3b989128",
                               :file-versions/model-number "foobar",
                               :file-versions/format

                               {:db/id       277076930207550,
                                :format/id
                                #uuid "5727d2de-6c83-4e01-bdd9-239933dfa31b",
                                :format/name "foobar"},
                               :file-versions/body
                               [{:db/id              277076930207703,
                                 :body/id
                                 #uuid "5727d2de-cbd8-4cd3-a248-0b51a47fbc99",
                                 :body/semantic-type :photograph,
                                 :body/body-binaries
                                 [{:db/id                    277076930207704,
                                   :body-binary/id
                                   #uuid "5727d2de-6562-471e-a1e3-2ea15d84d194",
                                   :body-binary/purpose      :original,
                                   :body-binary/content-type "foobar",
                                   :body-binary/remote-file
                                   "597b61e5f6a1dc676ff0f8068ef80536f5129e25.jpg",
                                   :body-binary/size         9303}]}],
                               :file-versions/primary-image {:db/id 277076930207703},
                               :file-versions/file-metas
                               [{:db/id          277076930207503,
                                 :file-meta/id   #uuid "5727d2de-6a11-487c-b598-bc50d93ca4ec"
                                 ,
                                 :file-meta/name "foobar",
                                 :file-meta/root
                                 {:db/id          277076930207488,
                                  :file-meta/id   #uuid "5727d2de-452e-4a41-9235-f1f63b501565",
                                  :file-meta/name "foobar"}}]},
                              :user-file/nickname "foobar"}
                             {:db/id                   277076930208300,
                              :user-file/id            #uuid "58193196-b519-4658-a678-4f6d28ba12a1",
                              :user-file/nickname      "foobar",
                              :user-file/serial-number "foobar",
                              :user-file/file-versions
                              {:db/id                      277076930208234,
                               :file-versions/id           #uuid "5807b64a-986d-488c-ba45-8ee8383b1c2a",
                               :file-versions/model-number "foobar",
                               :file-versions/format
                               {:db/id       277076930207624,
                                :format/id
                                #uuid "5807b679-d8c1-4a5b-8e25-10e8e5a82b24",
                                :format/name "foobar"},
                               :file-versions/file-metas
                               [{:db/id        277076930207498,
                                 :file-meta/id #uuid "5727d2de-53a7-42be-afbf-55ca42f1a7d9",
                                 :file-meta/name
                                 "foobar",
                                 :file-meta/root
                                 {:db/id          277076930207493,
                                  :file-meta/id   #uuid "5727d2de-c333-410c-8932-ddb892ed191a",
                                  :file-meta/name "Small Appliance"}}]}}
                             {:db/id                   277076930208301,
                              :user-file/id            #uuid "581a35a5-5cf9-4cc3-b8dc-6c7d492267ad",
                              :user-file/nickname      "foobar",
                              :user-file/serial-number "foobar",
                              :user-file/file-versions
                              {:db/id                      277076930208233,
                               :file-versions/id           #uuid "5807b32a-7cda-4204-9417-9b4562b97274",
                               :file-versions/model-number "foobar",
                               :file-versions/format
                               {:db/id       277076930207609,
                                :format/id
                                #uuid "5727d2de-9dcd-46e0-82c7-54bb07eceb78",
                                :format/name "foobar"},
                               :file-versions/file-metas
                               [{:db/id          277076930207501,
                                 :file-meta/id   #uuid "5727d2de-38dd-426c-8ddb-b0f93f246815",
                                 :file-meta/name "foobar",
                                 :file-meta/root
                                 {:db/id          277076930207488,
                                  :file-meta/id   #uuid "5727d2de-452e-4a41-9235-f1f63b501565",
                                  :file-meta/name "foobar"},
                                 :file-meta/body
                                 [{:db/id              277076930208634,
                                   :body/id
                                   #uuid "58215743-93e0-4abb-adf7-c69483b89923",
                                   :body/semantic-type :nameplate-help-photo,
                                   :body/title         "foobar",
                                   :body/description
                                   "foobar",
                                   :body/body-binaries
                                   [{:db/id               277076930208635,
                                     :body-binary/id
                                     #uuid "58215743-f86f-4e4d-874a-0c1448bfcf81",
                                     :body-binary/purpose :original,
                                     :body-binary/remote-file
                                     "foobar"}],
                                   :body/sort-order    0}
                                  {:db/id              277076930208637,
                                   :body/id
                                   #uuid "58215743-466f-45e8-b5b5-38ba2ebae686"
                                   ,
                                   :body/semantic-type :nameplate-help-video,
                                   :body/title
                                   "foobar",
                                   :body/description
                                   "foobar",
                                   :body/link
                                   "foobar",
                                   :body/sort-order    1}]}]}}]}]}}],
                       :user/user-invitations
                       [{:db/id                      277076930209598,
                         :user-invitation/id         #uuid "582b38cd-a2ed-446e-b64f-99d21f02442a",
                         :user-invitation/email      "foobar",
                         :user-invitation/first-name "foobar",
                         :user-invitation/last-name  "foobar",
                         :user-invitation/created-timestamp
                         #inst "2016-11-15T16:33:17.113-00:00",
                         :user-invitation/sent-timestamp

                         #inst "2016-11-15T16:33:24.859-00:00"}]}]
      (is (= pull-result (p/pluck {} query pull-result))))))
