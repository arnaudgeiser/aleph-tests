(ns aleph-tests.core
  (:require [aleph.tcp :as tcp]
            [aleph.http :as http]
            [clj-commons.byte-streams :as bs]
            [clojure.java.io :as io]
            [manifold.deferred :as d]
            [manifold.stream :as s])
  (:import [io.netty.buffer Unpooled]))

(def tcp-port 9000)
(def ws-port 9001)

(def buffer (volatile! (Unpooled/compositeBuffer)))
(def received (atom 0))

(defn tcp-handler [s _]
  (s/consume #(locking buffer (.addComponent @buffer true %)) s))

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

(defn make-tcp-client []
  @(tcp/client {:port tcp-port
                :host "localhost"}))

(defn start-tcp-clients []
  (dotimes [_ 1000]
    (future
      (let [client (make-tcp-client)]
        (while true
          (Thread/sleep 10)
          @(s/put! client (urandom 240)))))))

(defn send-buffer [client]
  (locking buffer
    (doseq [bb (bs/to-byte-buffers @buffer {:chunk-size (* 64 1024)})]
      @(s/put! client bb))
    (vreset! buffer (Unpooled/compositeBuffer))))

(defn start-sender [http-client]
  (future
    (while true
      (Thread/sleep 40)
      (send-buffer http-client))))

(defn start []
  (let [tcp-server  (tcp/start-server #'tcp-handler {:port tcp-port :raw-stream? true})
        http-server (http/start-server #'ws-handler {:port ws-port :raw-stream? true})
        http-client @(http/websocket-client (str "ws://localhost:" ws-port))]
    (start-tcp-clients)
    (start-sender http-client)))

(comment
  (prn (format "%d MiB received" (int (/ @received 1024 1024))))
  (start))
