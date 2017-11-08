(ns minimap.base64
  (:require [byte-transforms :as bt])
  (:import [java.nio.charset Charset]))

(defn decode-string
  "Take a Base64-encoded string, decode it into bytes, and return another string.
  (The bytes are interpreted as UTF-8, unless charset is specified.)"
  ([^String encoded]
   (decode-string encoded "UTF-8"))
  ([^String encoded ^String charset]
   (let [decoded-bytes (bt/decode (.getBytes encoded) :base64)]
     (String. ^bytes decoded-bytes charset))))

(defn encode-bytes ^bytes
  [^bytes raw]
  "Take some bytes, encode them as Base64, and return another byte array."
  (bt/encode raw :base64))

(defn encode-string
  "Take a string, encode its bytes as Base64, and return another string."
  [^String decoded]
  (-> (.getBytes decoded)
      (encode-bytes)
      (String. "ASCII")))
