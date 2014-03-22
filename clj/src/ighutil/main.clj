(ns ighutil.main
  "Main entry point - dispatches to subcommands"
  (:require [cliopatra.command :as command])
  (:gen-class
   :name ighutil.main))

(defn- subcommand-name-to-symbol [^String s]
  (->> s
       (str "ighutil.subcommands.")
       symbol))

(def command-names (sort ["annotate-vdj"
                          "calculate-match-probability"
                          "count-alignment-ties"
                          "count-mismatches"
                          "distinct-by-vdjcdr3"
                          "enumerate-mutations"
                          "identify-motif"
                          "kmer-matrix"
                          "mutation-correlation"
                          "aligned-base-composition"
                          "mutations-by-site"
                          "reset-primary"]))

(defn -main [& args]
  (let [command-syms (map subcommand-name-to-symbol command-names)]
    (apply require command-syms)
    (command/dispatch command-syms args)))
