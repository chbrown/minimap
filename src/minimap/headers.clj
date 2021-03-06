(ns minimap.headers
  (:require [clojure.string :as str]
            [minimap.base64 :as base64]
            [instaparse.core :as insta])
  (:import (java.time ZonedDateTime)
           (java.time.format DateTimeFormatter)))

; http://tools.ietf.org/html/rfc822#section-3
(defn decode-quopri
  "Decodes quoted-printable"
  [^String encoded ^String charset]
  (let [source (.getBytes encoded)
        c (count source)
        _ (assert (= c (count encoded))) ; since we should only have ascii
        target (byte-array c)]
    (loop [i 0 j 0]
      (if (< i c)
        (if (= 61 (aget source i)) ; '='
          (when (< (+ i 2) c) ; do we have two bytes following the = escape?
            (let [a (aget source (+ i 1))
                  b (aget source (+ i 2))]
              (if (or (= 10 a) (= 13 a))
                (recur (+ i 3) j) ; soft line return, just remove
                (let [code (Integer/parseInt (str (char a) (char b)) 16)
                      new-byte (if (> code 127) (- code 256) code)]
                  (aset-byte target j new-byte)
                  (recur (+ i 3) (+ j 1))))))
          (do
            (aset-byte target j (aget source i))
            (recur (inc i) (inc j))))
        (String. target 0 j charset)))))

; see https://www.ietf.org/rfc/rfc2047.txt
(defn decode-rfc2047
  "Decodes non-ascii headers values according to RFC2047. There can be several encoded words in a single string."
  [possibly-encoded]
  (str/replace
   possibly-encoded
   #"=\?([^?]+)\?([BbQq])\?([^\? ]*)\?="
   (fn [[_ charset encoding data]]
     (case encoding
       ("B" "b") (base64/decode-string data charset)
       ("Q" "q") (decode-quopri data charset)))))

(defn unfold
  "Unfold headers from raw text. Returns a list of header lines"
  [raw]
  {:pre [(string? raw)]}
  (reverse
   (loop [[line & ls] (str/split raw #"\r\n")
          [current & cs :as acc] nil]
     (cond
          ; empty line => end of headers. if line is nil, they forgot the empty line but that's OK.
       (or (= "" line) (nil? line)) acc
          ; first char is space or tab, this line is a folding continuation
       (#{\space \tab} (first line))
       (recur ls (cons (str current " " (str/trim line)) cs))
          ; new header field + value
       :else (recur ls (cons line acc))))))

(defn namevalue
  [^String headerline]
  (let [idx (.indexOf headerline ":")]
    (when (= -1 idx)
      (throw (ex-info "Weird header" {:line headerline})))
    [(subs headerline 0 idx) (str/trim (subs headerline (inc idx)))]))

(def time-formatters
  (map #(DateTimeFormatter/ofPattern %) ["EEE, dd MMM yyyy HH:mm:ss Z"
                                         "EEE, dd MMM yyyy HH:mm:ss z"
                                         "dd MMM yyyy HH:mm:ss Z"]))

(defn decode-date
  "RFC822"
  [s]
  (let [clean (str/replace s #" \([^\)]+\)" "")
        date (some #(try (ZonedDateTime/parse clean %)
                         (catch Exception e nil)) time-formatters)]
    date))

; https://www.cs.tut.fi/~jkorpela/rfc/822addr.html
(def address-parser (insta/parser
                     "S = ADRESS (<white> <','> <white> ADRESS)*

ADRESS : (name? <white> <'<'> email <'>'>)
       | email
       | <'undisclosed-recipients:;'>

name : QUOTED | PHRASE

<ATOM> = #'[^()<>@,;:\\\\\"\\[\\]\\ ]+'

(* Quoted string can contain escaped chars, including quotes *)
<QUOTED> = <'\"'> INQUOTE <'\"'>
<INQUOTE> = #'([^\\\"\\\\]|\\\\.)+'

email = ATOM '@' ATOM ('.' ATOM)*

(* Phrases like 'Alex Lebrun' in 'Alex Lebrun <alex@wit.ai>' *)
<PHRASE> = #'[^<,\"]+'

white = #' *'
"))

(defn decode-address
  [raw]
  (let [tree (address-parser raw)]
    (if (vector? tree)
      ; successful parse
      #_(prn tree)
      (map (fn [address] (into {} (for [[tag & more] (rest address)]
                                    [tag (str/join more)])))
           (rest tree)) ; first item is :S
      (prn "ERROR" raw))))

(defn decode-structured
  "Decodes structured fields such as Date"
  [[name value]]
  (let [rules {"Date" decode-date
               "From" decode-address
               "To" decode-address}]
    [name ((or (rules name) identity) value)]))

(defn parse
  [raw]
  (->> (unfold raw)
       (map namevalue)
       (map (fn [[n v]] [n (decode-rfc2047 v)]))
       (map decode-structured)))
