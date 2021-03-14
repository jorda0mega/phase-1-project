(ns com.github.jorda0mega.bitcoin-game.interaction
  (:require
    [com.github.jorda0mega.bitcoin-game.state :refer [initial-state state]]
    [com.github.jorda0mega.bitcoin-game.html :as html]
    [goog.dom :as gdom]
    [taoensso.timbre :as timbre]
    [goog.functions :as gfunctions]
    [oops.core :refer [ocall oget oset!]]
    [ajax.core :refer [GET]]))

(defn set-app-html!
  "Sets the application html"
  [html-str]
  (let [el (gdom/getElement "appContainer")]
    (oset! el "innerHTML" html-str)))

(defn render-ui! [_ _kwd _prev-state new-state]
  (timbre/trace "getting new state")
  (timbre/trace new-state)
  (set-app-html! (html/BitcoinGame new-state)))

(defn start-game-success [new-price]
  (swap! state (fn [current-state]
                 (if-not (:is-playing-game? current-state)
                   current-state
                   (assoc current-state :price new-price)))))

(defn fetch-bitcoin-price-error [{:keys [status status-text]}]
  (timbre/error "bad request to bitcoin charts: " status " " status-text))

(defn profit-from-vote
  "determines whether the vote was correct/wrong and adds/removes profits accordingly"
  [vote-result bid]
  (swap! state (fn [current-state]
                 (let [current-profit (:profit current-state)]
                   (if vote-result
                     (assoc current-state :profit (+ current-profit bid))
                     (assoc current-state :profit (- current-profit bid)))))))

(defn vote-up-success
  "handle voting up "
  [new-price]
  (timbre/info "calling vote up")
  (swap! state (fn [current-state]
                 (assoc current-state :price new-price)
                 (profit-from-vote (>= new-price (:price current-state)) 100))))

(defn vote-down-success
  "handle voting up "
  [new-price]
  (timbre/info "calling vote down")
  (swap! state (fn [current-state]
                 (assoc current-state :price new-price)
                 (profit-from-vote (<= new-price (:price current-state)) 100))))

(defn fetch-bitcoin-price
  "ajax request to fetch bitcoin current price"
  [success-fn]
  (GET "https://api.coinlore.net/api/ticker/?id=90" {:handler         success-fn
                                                     :error-handler   fetch-bitcoin-price-error
                                                     :response-format :json
                                                     :keywords?       true}))

(defn start-game []
  (swap! state (fn [state]
                 (assoc state :is-playing-game? true)))
  (fetch-bitcoin-price start-game-success))

(defn reset-game []
  (reset! state initial-state))

(defn vote-up
  "voting that the bitcoin price will go up"
  []
  (timbre/info "I bet the bitcoin price is going up")
  (fetch-bitcoin-price vote-up-success))

(defn vote-down
  "voting that the bitcoin price will go down"
  []
  (timbre/info "I bet the bitcoin price is going down")
  (fetch-bitcoin-price vote-down-success))

(defn handle-game-started []
  (oset! (gdom/getElement "startButton") "style.display" "none")
  (oset! (gdom/getElement "divPrice") "style.display" "inline-block")
  (oset! (gdom/getElement "divProfit") "style.display" "block")
  (oset! (gdom/getElement "buttonPanel") "style.display" "inline-block")
  (oset! (gdom/getElement "resetButton") "style.display" "inline-block")
  (oset! (gdom/getElement "divRangeSlider") "style.display" "block"))

(defn handle-game-state [_ _kwd _prev-state new-state]
  (let [is-playing-game? (:is-playing-game? new-state)]
    (when is-playing-game?
      (handle-game-started))))

(defn click-app-container [js-evt]
  (let [target-el (oget js-evt "target")
        start? (ocall target-el "classList.contains" "start")
        reset? (ocall target-el "classList.contains" "reset")
        going-up? (ocall target-el "classList.contains" "upBtn")
        going-down? (ocall target-el "classList.contains" "downBtn")]
    (cond
      start? (start-game)
      reset? (reset-game)
      going-up? (vote-up)
      going-down? (vote-down)
      :else nil)))

;; ---------------------------------------------
;; Public API

(defn trigger-render! []
  (swap! state identity))

(def init-dom-events!
  (gfunctions/once
    (fn []
      (ocall (gdom/getElement "appContainer") "addEventListener" "click" click-app-container))))

(def init-watchers!
  (gfunctions/once
    (fn []
      (add-watch state :render-ui render-ui!)
      (add-watch state :handle-game-state handle-game-state))))