(ns kmeans-clj.core
  "A simple k-means clustering implementation."
  (:require [incanter.core :as i]
            [kmeans-clj.util :refer :all]
            [clojure.core.async :refer [chan to-chan <!!]]))

(defn cluster-vector
  [fe cluster]
  (i/div (reduce i/plus (map fe cluster)) (count cluster)))

(defn k-means
  "Performs k-means clustering on the elements into k clusters until
   convergence or the maximum number of iterations is reached. Function
   distance is a pairwise distance function, and fe a feature extractor
   that returns a vector for a given element.
   
   Note: This function currently does some group-by and seq manipulation
   to form new clusters. This may however be a prime candidate for STM
   and a ref."
  [elements distance fe k max-iter]
  (loop
    [clusters-1 nil
     clusters (partition-all (quot (count elements) k) (shuffle elements))
     iter 0]
    (if
      (or (= clusters clusters-1) (= max-iter iter))
      (concat (seq clusters) (repeat (- k (count clusters)) []))
      (let [input (to-chan elements)
            output (chan)
            results (sink output)
            _ (<!! (parallel
                     (.availableProcessors (Runtime/getRuntime))
                     (fn [e] [(argmin
                                #(distance (fe e)
                                           (cluster-vector fe %))
                                clusters)
                              e])
                     input
                     output))]
        (recur
          clusters
          (set (->>
                 @results
                 (group-by first)
                 (fmap #(map second %))
                 vals))
          (inc iter))))))
