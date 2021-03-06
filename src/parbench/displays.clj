(ns parbench.displays
  (:use [rosado.processing]
        [clojure.contrib.seq-utils :only [find-first]])
  (:require [parbench.requests-state :as rstate])
  (:import  [java.util TimerTask Timer]
            [javax.swing JFrame]
            [processing.core PApplet]))

(def colors {
  :yellow      [[210 210   0] [255 255   0]]
  :dark-gray   [[105 105 105] [120 120 120]]
  :light-gray  [[220 220 220] [235 235 235]]
  :blue        [[120 120 255] [150 150 255]]
  :white       [[255 255 255] [240 240 240]]
  :red         [[255 105 105] [250 120 120]]
  :black       [[  0   0   0] [255   0   0]]})

(defn- status-color
  "Color tuple (fill, outline) based on HTTP status codes"
  [status]
  (cond
    (<= 200 status 299) (colors :dark-gray)
    (<= 300 status 399) (colors :blue)
    (<= 400 status 499) (colors :white)
    (<= 500 status 599) (colors :red)
    :else               (colors :black) ))

(defn- render-square
  "Render an individual square in the papplet"
  [col row scale request]
  (let [state  (:state request)
        [fill-color stroke-color]
        (cond (= state :untried)   (colors :light-gray)
              (= state :requested) (colors :yellow)
              (= state :failed)    (colors :black)
              (= state :responded) (status-color (:status request))
              :else                (colors :black))]
        (apply fill-float   fill-color)
        (apply stroke-float stroke-color)
            (io!
              (rect (* scale col) (* scale row) scale scale))))

    (defn- render-request
      "Render an individual request if it needs to be rendered"
      [req-ref scale]
      (let [{col :x row :y} @req-ref]
        (dosync
          (cond (not (:rendered @req-ref))
            (alter req-ref assoc :rendered true)))
      (render-square col row scale @req-ref)))

    (defn- status-draw
      "Called on each render, renders all requests"
      [dst reqs-state scale]
      (doseq [req-ref (flatten (:grid @reqs-state))]
        (render-request req-ref scale)))

    (defn- create-pb-applet
      "Create an applet for processing, calling draw-fn on every draw cycle"
      [reqs-state width height scale draw-fn]
      (proxy [PApplet] []
        (setup []
          (binding [*applet* this]
            (size width height)
            (framerate 10)))
         (draw []
           (binding [*applet* this]
           (status-draw this reqs-state scale)))))

    (defn- block-till-all-rendered
      "Intentionally block until all requests are rendered, useful for prewarming"
      [reqs-state]
      (let [unfinished? (find-first
                          #(not (:rendered (deref %1)))
                          (flatten (:grid @reqs-state)))]
        (cond unfinished?
          (do
            (Thread/sleep 100)
            (recur reqs-state)))))

(defn initialize-graphics
  "Sets up GUI output via Swing + Processing"
  [reqs-state width height scale draw-fn]
  (let [pb-applet   (create-pb-applet reqs-state width height scale draw-fn)
        swing-frame (JFrame. "Parbench")]
    (.init pb-applet)
      (doto swing-frame
        (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
        (.setSize width height)
        (.add pb-applet)
        (.pack)
        (.show)))
  ; block till GUI loaded
  (block-till-all-rendered reqs-state))

(defn status-code-gui
  "Displays a grid colored by request status code"
  [reqs-state ui-opts]
  (let [scale  (:scale ui-opts)
        width  (* scale (:requests    @reqs-state))
        height (* scale (:concurrency @reqs-state))]
    (initialize-graphics reqs-state width height scale status-draw)))

(defn console-full
  "Dumps the whole grid to the console. Warning: Extremely Verbose."
  [reqs-state ui-opts]
  (let [task (proxy [TimerTask] []
       (run [] (println @reqs-state)))]
  (.scheduleAtFixedRate (Timer.) task (long 0) (long 1000))))

(defn- format-time-duration
  "Formats a time duration as HH:MM:SS.Millis"
  [duration]
  (let [duration-secs (int (/ duration 1000))
        hours         (int (/ duration-secs 3600))
        minutes       (int (/ (mod duration-secs 3600) 60))
        seconds       (int (mod duration-secs 60))
        millis        (rem duration 1000)]
       (format "%02d:%02d:%02d.%04d" hours minutes seconds millis)))

(defn display-final-stats
  "Print out a final summary of the current state of all requests"
  [reqs-state]
  (let [state    @reqs-state
        stats    (rstate/stats reqs-state)
        started  (:bench-started-at state)
        ended    (:bench-ended-at   state)
        duration (- ended started)
        reqs-sec (float (/ (:responded stats) (/ duration 1000)))]
    (io!
      (println "Total Runtime: " (format-time-duration duration))
      (println "Reqs/sec:"       (format "%f/sec" reqs-sec)))))

(defn display-live-console-stats
  "Print out a live console display of the current state of all requests"
  [reqs-state]
  (io! (println (rstate/stats reqs-state))))

(defn console
  "Dumps a summary of stats to the console"
  [reqs-state ui-opts]
  (let [task (proxy [TimerTask] []
         (run []
           (if (rstate/complete? reqs-state)
             (do
               (display-live-console-stats reqs-state)
               (display-final-stats reqs-state)
               (.cancel this))
             (display-live-console-stats reqs-state))))]
    (.scheduleAtFixedRate (Timer.) task (long 0) (long 1000))))
