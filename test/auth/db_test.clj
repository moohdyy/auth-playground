(ns auth.db-test
  (:require [clojure.test :refer :all]
            [hikari-cp.core :as cp]
            [jdbc.core :as jdbc]
            [auth.db.schema :as schema]
            [auth.db.user :as u]
            [auth.db.tenant :as t]))

(deftest auth-db-test

  (let [ds (cp/make-datasource {:adapter  "h2"
                                :url      "jdbc:h2:mem:authtest"
                                :username "sa"
                                :password ""})
        conn (jdbc/connection ds)]

    (try

      (testing "Create tables"
        (is (= (schema/h2-create-tables conn)
               0)))

      (testing "User Management"

        (is (= (u/add-user conn {:username "u1" :fullname "u1fn" :email "u1@email.com"})
               1))

        (is (= (u/add-user conn {:username "u2" :fullname "u2fn" :email "u2@email.com"})
               1))

        (is (= (u/get-user conn {:username "u1"})
               {:username "u1" :fullname "u1fn" :email "u1@email.com"}))

        (is (= (u/get-users conn)
               [{:username "u1" :fullname "u1fn" :email "u1@email.com"}
                {:username "u2" :fullname "u2fn" :email "u2@email.com"}]))

        (is (= (u/rename-user conn {:username "u2" :new-username "u2n"})
               1))

        (is (= (u/get-user conn {:username "u2n"})
               {:username "u2n" :fullname "u2fn" :email "u2@email.com"}))

        (is (= (u/set-user-info conn {:username "u2n" :fullname "u2nfn" :email "u2n@email.com"})
               1))

        (is (= (u/get-user conn {:username "u2n"})
               {:username "u2n" :fullname "u2nfn" :email "u2n@email.com"}))

        (is (= (u/delete-user conn {:username "u2n"})
               1))

        (is (not (u/get-user conn {:username "u2n"})))

        (is (= (u/get-users conn)
               [{:username "u1" :fullname "u1fn" :email "u1@email.com"}])))

      (testing "User Authentication"

        (is (u/set-password conn {:username "u1" :password "p1"})
            1)

        (is (u/authenticate conn {:username "u1" :password "p1"})
            {:status :success
             :user   {:username "u1" :fullname "u1fn" :email "u1@email.com"}})

        (is (u/authenticate conn {:username "ux" :password "p1"})
            {:status :failed
             :cause  :unknown-user})

        (is (u/authenticate conn {:username "u1" :password "px"})
            {:status :failed
             :cause  :invalid-password})

        (is (u/change-password conn {:username "u1" :password "p1" :new-password "pn"})
            {:status :success})

        (let [expire-seconds 5
              secret "server-hmac-secret"
              result (u/obtain-token conn secret expire-seconds {:username "u1" :password "pn"})]

          (is (= (:status result) :success))

          (is (= (:user result)
                 {:username "u1" :fullname "u1fn" :email "u1@email.com"}))

          (let [result (u/authenticate-token secret (:token result))]

            (is (= (:status result) :success))

            (is (= (:user result)
                   {:username "u1" :fullname "u1fn" :email "u1@email.com"})))

          (let [result (do
                         (Thread/sleep (* expire-seconds 1000 1.2))
                         (u/authenticate-token secret (:token result)))]

            (is (= result
                   {:status :failed
                    :type   :validation
                    :cause  :exp}))))

        (let [reset-token (u/obtain-reset-token conn "secret" 1 {:username "u1"})
              reset (u/reset-password conn "secret" {:new-password "np" :token reset-token})
              exp-reset (do
                          (Thread/sleep 1200)
                          (u/reset-password conn "secret" {:new-password "np" :token reset-token}))]

          (is (= (:status reset) :success))
          (is (= (:status exp-reset) :failed))))

      (testing "Tenant Management"

        (is (= (t/add-tenant conn {:name "t1" :config {:k1 :v1 :k2 :v2}})
               1))

        (is (= (t/add-tenant conn {:name "t2" :config {:k1 :v1 :k2 :v2}})
               1))

        (is (= (t/get-tenant conn {:name "t1"})
               {:name "t1" :config {:k1 :v1 :k2 :v2}}))

        (is (= (t/get-tenants conn)
               [{:name "t1" :config {:k1 :v1 :k2 :v2}}
                {:name "t2" :config {:k1 :v1 :k2 :v2}}]))

        (is (= (t/rename-tenant conn {:name "t2" :new-name "t2n"})
               1))

        (is (not (t/get-tenant conn {:name "t2"})))

        (is (= (t/get-tenant conn {:name "t2n"})
               {:name "t2n" :config {:k1 :v1 :k2 :v2}}))

        (is (= (t/set-tenant-config conn {:name "t2n" :config {:nk1 :v1 :nk2 :v2}})
               1))

        (is (= (t/get-tenant conn {:name "t2n"})
               {:name "t2n" :config {:nk1 :v1 :nk2 :v2}}))

        (is (= (t/delete-tenant conn {:name "t2n"})
               1))

        (is (not (t/get-tenant conn {:name "t2"}))))

      (testing "Tenant User Management"

        (is (= (u/assign-tenant conn {:username "u1"} {:name "t1"})
               1))

        (is (= (t/add-tenant conn {:name "t2" :config {:k1 :v1 :k2 :v2}})
               1))

        (is (= (u/assign-tenant conn {:username "u1"} {:name "t2"})
               1))

        (is (= (u/get-user-tenants conn {:username "u1"})
               [{:name "t1" :config {:k1 :v1 :k2 :v2}}
                {:name "t2" :config {:k1 :v1 :k2 :v2}}]))

        (is (= (u/unassign-tenant conn {:username "u1"} {:name "t2"})
               1))

        (is (= (u/get-user-tenants conn {:username "u1"})
               [{:name "t1" :config {:k1 :v1 :k2 :v2}}]))

        (is (= (t/get-tenant-users conn {:name "t1"})
               [{:username "u1" :fullname "u1fn" :email "u1@email.com"}]))

        (is (= (u/add-user conn {:username "u2" :fullname "u2fn" :email "u2@email.com"})
               1))

        (is (= (u/assign-tenant conn {:username "u2"} {:name "t1"})
               1))

        (is (= (t/get-tenant-users conn {:name "t1"})
               [{:username "u1" :fullname "u1fn" :email "u1@email.com"}
                {:username "u2" :fullname "u2fn" :email "u2@email.com"}])))

      (catch Exception e
        (throw e))

      (finally
        (.close conn)
        (cp/close-datasource ds)))

    ))


