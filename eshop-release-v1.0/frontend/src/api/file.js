import http from './http'

/**
 * 登录用户上传图片（评价晒图等）。
 * 不要手动设置 Content-Type，交由浏览器自动带 multipart boundary。
 */
export const uploadUserImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/files/upload', formData)
}
