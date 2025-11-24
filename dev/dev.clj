(ns dev
  (:require
   [clj-kondo.core :as clj-kondo]
   [clj-reload.core :as reload]))

(reload/init
  {:dirs ["src" "dev" "test"]})

(defn lint
  "Lint the entire project (src and test directories)."
  []
  (-> (clj-kondo/run! {:lint ["src" "test"]})
      (clj-kondo/print!)))

(defn refresh
  "Reloads and compiles he Clojure namespaces."
  []
  (reload/reload))
