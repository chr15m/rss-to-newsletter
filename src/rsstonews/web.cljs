(ns rsstonews.web
  (:require
    [shadow.resource :as rc]
    ["path" :as path]
    ["process" :as process]
    ["express" :as express]
    ["cookie-parser" :as cookies]
    ["body-parser" :as body-parser]
    ["serve-static" :as serve-static]
    ["express-session" :as session]
    ["morgan" :as morgan]
    ["rotating-file-stream" :as rfs]
    ["keyv" :as Keyv]
    [cljs.core.async :refer (go <!) :as async]
    [cljs.core.async.interop :refer-macros [<p!]]
    [applied-science.js-interop :as j]))

; fatal errors should bail and notify the user
(defn bail [msg]
  (js/console.error msg)
  (js/console.error "Server exit.")
  (js/process.exit 1))

(defn env [k & [default]]
  (or (aget js/process.env k) default))

(defonce keyv (Keyv. (env "DATABASE" "sqlite://./sessions.sqlite")))

(defn create-store [kv]
  (let [e (session/Store.)]
    (aset e "destroy" (fn [sid callback]
                        (go (<p! (j/call kv :destroy sid))
                            (callback))))
    (aset e "get" (fn [sid callback]
                    (go (callback
                          nil
                          (<p! (j/call kv :get sid))))))
    (aset e "set" (fn [sid session callback]
                    (go (<p! (j/call kv :set sid session))
                        (callback))))
    (aset e "touch" (fn [sid session callback]
                      (go (<p! (j/call kv :set sid session))
                          (callback))))
    (aset e "clear" (fn [callback]
                      (go (<p! (js/call kv :clear))
                          (callback))))
    e))

(defn add-default-middleware [app]
  ; set up logging
  (let [logs (str js/__dirname "/logs")
        access-log (.createStream rfs "access.log" #js {:interval "7d" :path logs})
        store (create-store keyv)]
    (.use app (morgan "combined" #js {:stream access-log}))
    ; set up sessions table
    (.use app (session #js {:secret (env "SESSION_SECRET" "DEVMODE")
                            :saveUninitialized false
                            :resave true
                            :cookie #js {:secure "auto"
                                         :httpOnly true
                                         ; 10 years
                                         :maxAge (* 10 365 24 60 60 1000)}
                            :store store})))
  ; configure sane server defaults
  (.set app "trust proxy" "loopback")
  ; use cookies
  (.use app (cookies (env "SESSION_SECRET" "DEVMODE")))
  ; json body parser
  (.use app (.json body-parser #js {:limit "10mb" :extended true :parameterLimit 1000}))
  app)

(defn add-default-routes [app]
  ; none yet here
  (.use app "/" (serve-static (path/join js/__dirname (if (env "NGINX_SERVER_NAME") "build" "public"))))
  app)

(defn reset-routes [app]
  (let [router app._router]
    (when router
      (print (str "Deleting " (aget app "_router" "stack" "length") " routes"))
      (set! app._router nil))
    (-> app
        (add-default-middleware)
        (add-default-routes))))

(defn create []
  (-> (express)
      (add-default-middleware)
      (add-default-routes)))

(defn serve [app]
  (let [host (env "BIND_ADDRESS" "127.0.0.1")
        port (env "PORT" "8000")
        srv (.bind (aget app "listen") app port host)]
    (js/Promise.
      (fn [res err]
        (srv (fn []
               (js/console.log "Web server started: " (str "http://" host ":" port))
               (res #js [host port])))))))
