import http from './http'

export const getVehicleConfigurator = (productId, config) => (
  http.get(`/vehicles/${productId}/configurator`, config)
)

export const quoteVehicle = (productId, payload, config) => (
  http.post(`/vehicles/${productId}/configurator/quote`, payload, config)
)
