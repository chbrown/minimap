(ns minimap.message-test
  (:require [clojure.test :refer [deftest testing is]]
            [minimap.message]))

(def msg {:bodystructure '({:encoding "7bit"
                            :charset "utf-8"
                            :content-type "text/plain"
                            :path 1}
                           {:encoding "7bit"
                            :charset "utf-8"
                            :content-type "text/html"
                            :path 2})})
