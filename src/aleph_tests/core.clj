(ns aleph-tests.core
  (:require [aleph.tcp :as tcp]
            [aleph.http :as http]
            [clojure.java.io :as io]
            [manifold.deferred :as d]
            [manifold.stream :as s])
  (:import [io.netty.buffer ByteBuf CompositeByteBuf Unpooled]))

(set! *warn-on-reflection* true)

(def tcp-port 9000)
(def ws-port 9001)

(def ws-limit (* 64 1024))

(defn aggregate-xf [xf]
  (let [state (volatile! (Unpooled/compositeBuffer))]
    (fn
      ([] (xf))
      ([result] (xf result))
      ([result chunk]
       (let [size (.readableBytes ^CompositeByteBuf @state)
             chunk-size (.readableBytes ^ByteBuf chunk)]
         (when (> (+ size chunk-size) ws-limit)
           (xf result @state)
           (vreset! state (Unpooled/compositeBuffer)))
         (.addComponent ^CompositeByteBuf @state true ^ByteBuf chunk)
         result)))))

(def aggregate-stream (s/stream 200))
(def received (atom 0))

(defn tcp-handler [s _]
  (s/connect s aggregate-stream))

(defn ws-handler [req]
  (-> (http/websocket-connection req)
      (d/chain' (fn [s]
                  (s/consume (fn [v] (swap! received + (count v))) s)))))

(defn urandom
  [n]
  (with-open [in (io/input-stream (io/file "/dev/urandom"))]
    (let [buf (byte-array n)
          _ (.read in buf)]
      buf)))

(def data (urandom 240))

(defn make-tcp-client []
  @(tcp/client {:port tcp-port
                :host "localhost"}))

(defn start-tcp-clients []
  (dotimes [_ 1000]
    (future
      (let [client (make-tcp-client)]
        (while true
          (Thread/sleep 10)
          @(s/put! client data))))))

(defn send-buffer [client]
  (future
    (->> aggregate-stream
         #_
         (s/transform aggregate-xf)
         (s/consume #(s/put! client %)))))

(defn start []
  (let [tcp-server  (tcp/start-server #'tcp-handler {:port tcp-port :raw-stream? true :epoll? true})
        http-server (http/start-server #'ws-handler {:port ws-port :raw-stream? true :epoll? true})
        http-client @(http/websocket-client (str "ws://localhost:" ws-port) {:epoll? true})]
    (start-tcp-clients)
    (send-buffer http-client)))

(comment
  (prn (format "%d MiB received" (int (/ @received 1024 1024))))
  (start)
  (require '[clj-async-profiler.core :as prof])

  (prof/profile-for 10)
  (prof/serve-ui 8080))
