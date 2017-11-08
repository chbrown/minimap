(ns minimap.message
  (:require [clojure.string :as string]
            [clojure.data.json :as json :refer [JSONWriter]]
            [clojure.java.io :as io])
  (:import (java.time ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(def ^DateTimeFormatter rfc822-formatter
  (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z"))

;; add a custom encoder for org.joda.time.DateTime:
(extend-protocol JSONWriter
  ZonedDateTime
  (-write [d out]
    (json/write (.format rfc822-formatter d) out)))

(def common-headers #{"Subject" "From" "To" "Date" "Cc"})

(defn text-parts
  "Returns [path-of-text/pain path-of-text/html]
  where path references a body part, e.g. 1.1"
  [{:keys [bodystructure] :as msg}]
  {:pre [bodystructure]}
  (let [tp (first (filter #(= "text/plain" (:content-type %)) bodystructure))
        th (first (filter #(= "text/html" (:content-type %)) bodystructure))]
    [tp th]))

(defn assoc-common
  "Assoc common headers and flags like Subject, To"
  [msg]
  (-> msg
      (into (->> (:headers msg)
                 (filter (comp common-headers first))
                 (map (fn [[k v]] [((comp keyword string/lower-case) k) v]))))
      (cond-> ((set (:flags msg)) "\\Flagged") (assoc :flagged true))
      (cond-> ((set (:flags msg)) "\\Answered") (assoc :answered true))))

(defn store
  [msg]
  (spit (format "messages/%s.json" (:msg-id msg))
        (json/write-str msg))
  msg)

(defn store-meta
  [msg-id meta]
  (spit (format "meta/%s.json" msg-id)
        (json/write-str meta)))

(defn retrieve-obj
  [message-or-meta msg-id]
  (let [filename (format "%s/%s.json" message-or-meta msg-id)]
    (when (.exists (io/as-file filename))
      (-> (slurp filename)
          (json/read-str :key-fn keyword)))))

(defn update-meta
  [msg-id pred]
  (->> (retrieve-obj "meta" msg-id)
       (merge {:msg-id msg-id})
       pred
       (store-meta msg-id)))

(defn stored?
  [msg-id]
  (.exists (io/as-file (format "messages/%s.json" msg-id))))

(defn retrieve
  [msg-id]
  (when (stored? msg-id)
    (-> (retrieve-obj "messages" msg-id)
        (update-in [:date] #(ZonedDateTime/parse % rfc822-formatter))
        (assoc :meta (retrieve-obj "meta" msg-id)))))

(defn all-msg-ids
  []
  (->> (file-seq (io/file "messages"))
       (map #(.getName ^java.io.File %))
       (map #(re-find #"(\d+)\.json" %))
       (filter identity)
       (map second)))

(defn random
  []
  (-> (all-msg-ids)
      rand-nth
      retrieve))

(defn all-meta-ids
  []
  (->> (file-seq (io/file "meta"))
       (map #(.getName ^java.io.File %))
       (map #(re-find #"(\d+)\.json" %))
       (filter identity)
       (map second)))

(defn all-meta
  []
  (map #(into {:msg-id %} (retrieve-obj "meta" %)) (all-meta-ids)))

(defn lines
  "Returns a sequence of [start end] for each line in the plain text body, excluding the \n"
  [msg]
  (when-let [^String text (:plain msg)]
    (loop [idx (.indexOf text "\n" 0) start 0 acc []]
      (if (= -1 idx)
        (conj acc [start (count text)])
        (recur (.indexOf text "\n" (inc idx)) (inc idx) (conj acc [start idx]))))))
