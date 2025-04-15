(ns app.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [devtools.core :as devtools]))

;;; SETUP
(devtools/install!)


;;; LIFECYCLE

(defonce canvas (.querySelector js/document "#main-canvas"))

(def two-pi (* 2 js/Math.PI))

(defn calc-x-offset
  [shapes]
  (->> shapes
       (map (fn [{:keys [shape-type x radius]}]
              (if (= :circle shape-type)
                (- x radius)
                x)))
       (apply min)))

(defn calc-y-offset
  [shapes]
  (->> shapes
       (map (fn [{:keys [shape-type y radius]}]
              (if (= :circle shape-type)
                (- y radius)
                y)))
       (apply min)))

(defn calc-x-max
  [shapes]
  (->> shapes
       (map (fn [{:keys [shape-type x width radius]}]
              (if (= :circle shape-type)
                (+ x radius)
                (+ x width))))
       (apply max)))

(defn calc-y-max
  [shapes]
  (->> shapes
       (map (fn [{:keys [shape-type y height radius]}]
              (if (= :circle shape-type)
                (+ y radius)
                (+ y height))))
       (apply max)))


(defn render-shape
  [ctx x-offset y-offset]
  (fn [{:keys [shape-type x y width height radius fill]}]
    (let [x (- x x-offset)
          y (- y y-offset)
          _ (set! (.-fillStyle ctx) fill)]
      (case shape-type
        :rect (.fillRect ctx x y width height)
        :circle (do (.beginPath ctx)
                    (.arc ctx x y radius 0 two-pi)
                    (.fill ctx))))))


(defn calc-group-boundries
  [shapes x-offset y-offset]
  (->> (group-by :group shapes)
       (map (fn [[_ gr-shapes]]
              {:start-x (- (calc-x-offset gr-shapes) x-offset)
               :start-y (- (calc-y-offset gr-shapes) y-offset)
               :end-x (- (calc-x-max gr-shapes) x-offset)
               :end-y (- (calc-y-max gr-shapes) y-offset)}))))

(defn on-click
  [ctx groups]
  (fn [e]
    (let [x (.-clientX e)
          y (.-clientY e)]
      (doall
       (->> groups
            (map (fn [{:keys [start-x start-y end-x end-y]}]
                   (when (and (<= start-x x end-x) (<= start-y y end-y))
                     (set! (.-strokeStyle ctx) "#8213DC")
                     (.strokeRect ctx start-x start-y (- end-x start-x) (- end-y start-y))))))))))


(defn ^:export init []
  (go
    (let [resp (<! (http/get "shapes.edn"))
          shapes (:body resp)
          x-offset (calc-x-offset shapes)
          y-offset (calc-y-offset shapes)
          max-x (calc-x-max shapes)
          max-y (calc-y-max shapes)
          canvas-width (- max-x x-offset)
          canvas-height (- max-y y-offset)
          _ (set! (.-width canvas) canvas-width)
          _ (set! (.-height canvas) canvas-height)
          ctx (.getContext canvas "2d")
          groups (calc-group-boundries shapes x-offset y-offset)]
      (.addEventListener canvas "click" (on-click ctx groups))
      (doall
       (map (render-shape ctx x-offset y-offset) shapes)))))



(defn ^:dev/before-load before-load [])

(defn ^:dev/after-load after-load []
  (.removeEventListener canvas "click")
  (init))