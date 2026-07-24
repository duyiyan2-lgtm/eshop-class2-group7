import http from './http'

export const getAddresses = () => http.get('/addresses')

export const createAddress = (payload) => http.post('/addresses', payload)

export const updateAddress = (id, payload) => http.put(`/addresses/${id}`, payload)

export const setDefaultAddress = (id) => http.patch(`/addresses/${id}/default`)

export const removeAddress = (id) => http.delete(`/addresses/${id}`)
