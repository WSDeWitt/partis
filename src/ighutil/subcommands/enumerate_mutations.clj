(ns ighutil.subcommands.enumerate-mutations
  "Enumerate all mutations from germline.
   Output is a graph containing edges between nodes with subsets of mutations."
  (:import [net.sf.picard.reference
            ReferenceSequenceFileFactory
            ReferenceSequenceFile
            ReferenceSequence]
           [net.sf.samtools
            SAMRecord
            SAMFileReader
            SAMFileReader$ValidationStringency]
           [io.github.cmccoy.sam SAMUtils Mutation])
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [cliopatra.command :refer [defcommand]]
            [plumbing.core :refer [safe-get]]
            [ighutil.io :as zio]
            [ighutil.sam :as sam]
            [ighutil.ubtree :as ub]
            [ighutil.fasta :refer [extract-references]]))


(let [n-records (atom 0)]
  (defn- report! []
    (let [n (swap! n-records inc)]
      (when (= 0 (mod n 50000))
        (.print System/err (format "Read record %10d\r" n))))))


(defn- strip-allele [^String s]
  "Remove the allele from a string"
  (let [idx (.lastIndexOf s 42)]  ; 42 == '*'
    (if (< idx 0)
      s
      (.substring s 0 idx))))

(defn- non-primary [^SAMRecord r]
  (or (.getReadUnmappedFlag r) (.getNotPrimaryAlignmentFlag r)))

(defn identify-mutations-in-sam [records ref-map]
  "Identify mutations from reference sequence in a SAM file."
  (for [^SAMRecord read records]
    (let [ref-name (sam/reference-name read)
          ref-bases (safe-get ref-map ref-name)
          muts (SAMUtils/enumerateMutations read ref-bases)
          tag #(.getAttribute read ^String %)]
      {:name (sam/read-name read)
       :reference ref-name
       :mutations muts
       :n-mutations (count muts)
       :cdr3-length (tag "XL")
       :j-gene (tag "XJ")
       :count (tag "XC")})))

(defcommand enumerate-mutations
  "Enumerate mutations by V / J"
  {:opts-spec [["-i" "--in-file" "Source file" :required true
                :parse-fn io/file]
               ["-o" "--out-file" "Destination path"
                :parse-fn zio/writer :required true]
               ["-r" "--reference-file" "Reference file" :required true]]}
  (let [ref (-> reference-file
                io/file
                ReferenceSequenceFileFactory/getReferenceSequenceFile)
        ref-map (into {} (extract-references ref))]
    (with-open [sam (SAMFileReader. ^java.io.File in-file)]
      (.setValidationStringency
       sam
       SAMFileReader$ValidationStringency/SILENT)
      (let [muts (-> sam
                     .iterator
                     iterator-seq
                     (identify-mutations-in-sam ref-map))]
        (with-open [^java.io.Closeable out-file out-file]
          (csv/write-csv out-file [["sequence" "reference" "location" "type" "wt" "mut"]])
          (let [rows (for [{:keys [mutations name reference] :as read} muts
                           ^Mutation m mutations]
                       [name reference (.. m (getType) (getCode))
                        (.getPosition m)
                        (.getWildType m)
                        (.getMutant m)])]
            (csv/write-csv out-file rows)))))))
