import http from './http'

export const getUsers = ({
  current = 1,
  size = 20,
  keyword,
  role,
  status,
} = {}) => http.get('/admin/users', {
  params: { current, size, keyword, role, status },
})

export const updateUserStatus = (id, status) => (
  http.patch(`/admin/users/${id}/status`, { status })
)
