(ns pedestal-api.core
  (:require
   [pedestal-api
    [swagger]
    [content-negotiation]
    [error-handling]
    [request-params]
    [routes]]
   [potemkin :refer [import-vars]]))

(import-vars [pedestal-api.swagger
              annotate
              coerce-request
              validate-response
              swagger-json
              swagger-ui
              doc]

             [pedestal-api.content-negotiation
              negotiate-response]

             [pedestal-api.error-handling
              error-responses]

             [pedestal-api.request-params
              common-body
              body-params]

             [pedestal-api.routes
              defroutes])
