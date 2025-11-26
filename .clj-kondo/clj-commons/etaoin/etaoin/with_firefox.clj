(ns etaoin.with-firefox
  (:require [clj-kondo.hooks-api :as api]))

(defn with-firefox
  "Hook for etaoin.api/with-firefox macro.
  
  Signature: (with-firefox opts? bind & body)
  
  Transforms to: (let [bind nil] body...)
  
  This teaches clj-kondo that `bind` is a local binding available in the body."
  [{:keys [node]}]
  (let [children (rest (:children node))
        first-child (first children)
        second-child (second children)
        ;; Determine if first arg is opts or bind
        ;; If second arg exists and is a token (symbol), first arg is likely opts
        has-opts? (and first-child second-child 
                       (api/token-node? second-child))
        [bind body] (if has-opts?
                      ;; Has opts: (with-firefox opts bind body...)
                      [second-child (drop 2 children)]
                      ;; No opts: (with-firefox bind body...)
                      [first-child (rest children)])]
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
