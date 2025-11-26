(ns etaoin.with-firefox
  (:require [clj-kondo.hooks-api :as api]))

(defn with-firefox
  "Hook for etaoin.api/with-firefox macro.
  
  Signature: (with-firefox opts? bind & body)
  
  Transforms to: (let [bind nil] body...)
  
  This teaches clj-kondo that `bind` is a local binding available in the body."
  [{:keys [node]}]
  (let [children (rest (:children node))
        ;; Check if first arg is a map (opts) or symbol (bind)
        [opts bind body] (if (and (seq children)
                                  (api/map-node? (first children)))
                           ;; Has opts map: (with-firefox {...} bind body...)
                           [(first children) (second children) (drop 2 children)]
                           ;; No opts: (with-firefox bind body...)
                           [nil (first children) (rest children)])]
    (when-not bind
      (throw (ex-info "with-firefox requires a binding symbol" {})))
    (when-not (api/token-node? bind)
      (throw (ex-info "Binding must be a symbol" {:bind bind})))
    
    ;; Transform to (let [bind nil] body...)
    (let [new-node (api/list-node
                    (list*
                     (api/token-node 'let)
                     (api/vector-node [bind (api/token-node 'nil)])
                     body))]
      {:node new-node})))
