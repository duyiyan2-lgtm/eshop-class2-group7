import http from './http'

export const register = (payload) => http.post('/auth/register', payload)
export const login = (payload) => http.post('/auth/login', payload)
export const logout = () => http.post('/auth/logout')
export const getCurrentUser = () => http.get('/auth/me')
export const updateProfile = (payload) => http.put('/auth/me', payload)
