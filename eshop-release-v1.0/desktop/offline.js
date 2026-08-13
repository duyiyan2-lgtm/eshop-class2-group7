const parameters = new URLSearchParams(window.location.search)
const target = parameters.get('target')
const code = parameters.get('code') || 'UNKNOWN'
const message = parameters.get('message') || '无法连接到 E-Shop 服务'

document.querySelector('#error-detail').textContent = `${message}（错误码：${code}）`
document.querySelector('#retry-button').addEventListener('click', () => {
  if (target) window.location.replace(target)
})
