#!/usr/bin/env bb

(ns display-brightness-to-ha
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.core.async :as a]
   [cheshire.core :as json]
   [babashka.http-client :as http]
   [babashka.process :as p :refer [process]]))


(def HA-URL (System/getenv "HASS_SERVER"))
(def HA-TOKEN (System/getenv "HASS_TOKEN"))
(def ha-monitor-brightness-entity "input_number.primary_monitor_brightness")

;; Check if the required environment variables are set
(when-not (and HA-URL HA-TOKEN)
  (println "Please set the HASS_SERVER and HASS_TOKEN environment variables.")
  (System/exit 1))


(def default-headers
  {:Authorization (str "Bearer " HA-TOKEN)
   :Content-Type "application/json"})


(defn strip-scheme 
  "Strips the scheme (http or https) from a URL."
  [uri]
  (str/replace uri #"https?://" ""))


(def client
  (http/client (-> http/default-client-opts
                   (update-in [:request :headers] merge default-headers)
                   (assoc-in [:request :uri] {:scheme "https"
                                              :host (strip-scheme HA-URL)
                                              :path "/api"}))))


(defn ha-client
  ([path] (ha-client path {}))
  ([path opts]
   (->
    (http/request (merge {:uri {:path (str "/api" path)},
                          :client client}
                         opts))
    :body
    (json/parse-string true))))


(defn get-state [ha entity-id]
  (ha (str "/states/" (name entity-id))))


(defn call-service [ha domain service data]
  (ha (str "/services/" domain "/" service)
      {:method :post
       :body (json/generate-string data)}))


;;;; Display brightness listener

(defn start-lunar-listener []
  (let [proc (process {:err :inherit
                       :shutdown p/destroy-tree}
                      "lunar" "listen" "--json" "main")
        channel (a/chan (a/sliding-buffer 1)
                        (comp
                         (map #(json/parse-string % true))
                         (filter :brightness)
                         (map :brightness)))
        reader (io/reader (:out proc))]
    (a/go-loop []
      (let [line (.readLine reader)]
        (cond
          (nil? line) ; end of stream
          (do
            (println "Reader: End of Stream. Closing channel")
            (a/close! channel))

          ;; lunar has run into an error
          (not (str/starts-with? line "{"))
          (do
            (println "Reader: Non-JSON line:" line)
            (println "Terminating process.")
            (a/close! channel))

          ;; happy path
          (a/>! channel line) (recur)

          :else ; channel closed
          (do
            (println "Reader: channel closed.")
            (p/destroy-tree proc)
            (println "Closing reader.")
            (.close reader)))))
    channel))


(defn start-home-assistant-exporter [chan]
  (a/go-loop []
    (when-let [line (a/<! chan)]
      (println "Sending value:" line)
      (call-service ha-client "input_number" "set_value"
                    {:entity_id ha-monitor-brightness-entity
                     :value (* 100 line)})
      (recur))))


(println "Starting Lunar listener...")
(def lunar-chan (start-lunar-listener))
(def ha-chan (start-home-assistant-exporter lunar-chan))
#_(println "Process started. Listening for Lunar brightness changes...")
(a/<!! ha-chan) ; wait for the channel to close
(println "ha-chan closed. Stopping process.")


(comment
  (defn start-printer [chan]
    (a/go-loop []
      (if-let [line (a/<! chan)]
        (do
          (println "Received JSON:" line)
          (recur))
        (println "Printer: channel closed. Stopping printer"))))

  (start-printer lunar-chan)
  (a/close! lunar-chan)
  (a/close! ha-chan)

  (call-service ha-client "input_number" "set_value"
                {:entity_id "input_number.primary_monitor_brightness"
                 :value 69}))