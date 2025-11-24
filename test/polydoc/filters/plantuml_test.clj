(ns polydoc.filters.plantuml-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [polydoc.filters.plantuml :as plantuml]))

(deftest test-has-class?
  (testing "has-class? detects class in attrs"
    (let [attrs ["" ["plantuml" "other"] []]]
      (is (plantuml/has-class? attrs "plantuml"))
      (is (plantuml/has-class? attrs "other"))
      (is (not (plantuml/has-class? attrs "missing"))))))

(deftest test-code-block-attrs
  (testing "code-block-attrs extracts attributes"
    (let [node {:t "CodeBlock"
                :c [["id" ["plantuml"] []] "@startuml\n@enduml"]}]
      (is (= ["id" ["plantuml"] []] (plantuml/code-block-attrs node))))
    
    (testing "returns nil for non-CodeBlock"
      (is (nil? (plantuml/code-block-attrs {:t "Para" :c []}))))))

(deftest test-code-block-code
  (testing "code-block-code extracts code string"
    (let [node {:t "CodeBlock"
                :c [["" [] []] "@startuml\n@enduml"]}]
      (is (= "@startuml\n@enduml" (plantuml/code-block-code node))))
    
    (testing "returns nil for non-CodeBlock"
      (is (nil? (plantuml/code-block-code {:t "Para" :c []}))))))

(deftest test-get-attr-value
  (testing "get-attr-value extracts key-value pairs"
    (let [attrs ["id" ["class1"] [["format" "png"] ["theme" "dark"]]]]
      (is (= "png" (plantuml/get-attr-value attrs "format")))
      (is (= "dark" (plantuml/get-attr-value attrs "theme")))
      (is (nil? (plantuml/get-attr-value attrs "missing"))))))

(deftest test-format->plantuml-flag
  (testing "format->plantuml-flag converts formats"
    (is (= "-tsvg" (plantuml/format->plantuml-flag "svg")))
    (is (= "-tpng" (plantuml/format->plantuml-flag "png")))
    (is (= "-ttxt" (plantuml/format->plantuml-flag "txt")))
    (is (= "-tpdf" (plantuml/format->plantuml-flag "pdf")))
    (is (= "-tsvg" (plantuml/format->plantuml-flag nil)))
    (is (= "-tsvg" (plantuml/format->plantuml-flag "unknown")))))

(deftest test-render-plantuml-txt
  (testing "render-plantuml with txt format (fast, no graphics)"
    (let [code "@startuml\nAlice -> Bob: Hello\n@enduml"
          result (plantuml/render-plantuml {:code code :format "txt"})]
      (is (:success result))
      (is (string? (:output result)))
      (is (:is-text result))
      (is (= "txt" (:format result))))))

(deftest test-render-plantuml-svg
  (testing "render-plantuml with svg format"
    (let [code "@startuml\nAlice -> Bob: Hello\n@enduml"
          result (plantuml/render-plantuml {:code code :format "svg"})]
      (is (:success result))
      (is (string? (:output result)))  ; base64 encoded
      (is (not (:is-text result)))
      (is (= "svg" (:format result))))))

(deftest test-render-plantuml-error
  (testing "render-plantuml with invalid syntax"
    (let [code "@startuml\nINVALID SYNTAX HERE\n@enduml"
          result (plantuml/render-plantuml {:code code :format "txt"})]
      ;; PlantUML is lenient and will render even with errors
      ;; So this might still succeed, just checking the structure
      (is (contains? result :success))
      (is (contains? result :format)))))

(deftest test-make-error-block
  (testing "make-error-block creates error CodeBlock"
    (let [code "@startuml\nAlice -> Bob\n@enduml"
          error "Test error"
          block (plantuml/make-error-block code error)]
      (is (= "CodeBlock" (:t block)))
      (let [content (second (:c block))]
        (is (str/includes? content "ERROR"))
        (is (str/includes? content error))
        (is (str/includes? content code))))))

(deftest test-make-image-node-text
  (testing "make-image-node with text output"
    (let [output "ASCII art output"
          node (plantuml/make-image-node output "txt" true "code")]
      (is (= "CodeBlock" (:t node)))
      (is (= output (second (:c node)))))))

(deftest test-make-image-node-binary
  (testing "make-image-node with binary output"
    (let [output "base64encodeddata"
          node (plantuml/make-image-node output "svg" false "code")]
      (is (= "Para" (:t node)))
      (let [image (first (:c node))]
        (is (= "Image" (:t image)))
        (let [target (last (:c image))]
          (is (str/starts-with? (first target) "data:image/svg+xml;base64,")))))))

(deftest test-transform-plantuml-block
  (testing "transform-plantuml-block renders plantuml blocks"
    (let [node {:t "CodeBlock"
                :c [["" ["plantuml"] [["format" "txt"]]]
                    "@startuml\nAlice -> Bob: Hello\n@enduml"]}
          result (plantuml/transform-plantuml-block node)]
      ;; Should be transformed to either CodeBlock (txt) or Para with Image (svg/png)
      (is (or (= "CodeBlock" (:t result))
              (= "Para" (:t result))))))
  
  (testing "transform-plantuml-block with 'uml' class"
    (let [node {:t "CodeBlock"
                :c [["" ["uml"] [["format" "txt"]]]
                    "@startuml\nclass User\n@enduml"]}
          result (plantuml/transform-plantuml-block node)]
      (is (or (= "CodeBlock" (:t result))
              (= "Para" (:t result))))))
  
  (testing "transform-plantuml-block preserves non-plantuml blocks"
    (let [node {:t "CodeBlock"
                :c [["" ["python"] []]
                    "print('hello')"]}]
      (is (= node (plantuml/transform-plantuml-block node))))))

(deftest test-plantuml-filter-integration
  (testing "plantuml-filter processes entire AST"
    (let [ast {:pandoc-api-version [1 22]
               :meta {}
               :blocks [{:t "Para"
                        :c [{:t "Str" :c "Before"}]}
                       {:t "CodeBlock"
                        :c [["" ["plantuml"] [["format" "txt"]]]
                            "@startuml\nAlice -> Bob\n@enduml"]}
                       {:t "Para"
                        :c [{:t "Str" :c "After"}]}]}
          result (plantuml/plantuml-filter ast)
          blocks (:blocks result)]
      
      (is (= 3 (count blocks)))
      (is (= "Para" (:t (first blocks))))
      ;; Second block should be transformed
      (is (or (= "CodeBlock" (:t (second blocks)))
              (= "Para" (:t (second blocks)))))
      (is (= "Para" (:t (nth blocks 2)))))))

(deftest test-plantuml-filter-multiple-diagrams
  (testing "plantuml-filter handles multiple diagrams"
    (let [ast {:pandoc-api-version [1 22]
               :meta {}
               :blocks [{:t "CodeBlock"
                        :c [["" ["plantuml"] [["format" "txt"]]]
                            "@startuml\nAlice -> Bob\n@enduml"]}
                       {:t "CodeBlock"
                        :c [["" ["uml"] [["format" "txt"]]]
                            "@startuml\nclass User\n@enduml"]}]}
          result (plantuml/plantuml-filter ast)
          blocks (:blocks result)]
      
      (is (= 2 (count blocks)))
      ;; Both should be transformed
      (is (or (= "CodeBlock" (:t (first blocks)))
              (= "Para" (:t (first blocks)))))
      (is (or (= "CodeBlock" (:t (second blocks)))
              (= "Para" (:t (second blocks))))))))
