(ns klobb.markdown
  (:use [markdown.core]))

(defn to-html [markdown]
  (md-to-html-string markdown))

