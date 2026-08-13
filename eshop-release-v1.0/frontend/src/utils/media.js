const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
const isAndroidBuild = import.meta.env.VITE_APP_PLATFORM === 'android'

const getApiOrigin = () => {
  try {
    return new URL(apiBaseUrl, window.location.origin).origin
  } catch {
    return window.location.origin
  }
}

const mediaKeys = new Set(['mainImage', 'productImage', 'imageUrl'])

export const resolveMediaUrl = (value) => {
  if (!isAndroidBuild || typeof value !== 'string' || !value.trim()) return value

  const source = value.trim()
  if (/^(?:https?:|data:|blob:)/i.test(source)) return source

  try {
    return new URL(source.startsWith('/') ? source : `/${source}`, getApiOrigin()).href
  } catch {
    return value
  }
}

// CapacitorHttp can proxy API requests, but <img> still loads relative URLs from
// the WebView origin (https://localhost). Normalize image fields returned by the
// API so native builds always request media from the production API host.
export const normalizeMediaPayload = (value, key = '') => {
  if (Array.isArray(value)) {
    return value.map((item) => (
      key === 'imageUrls' ? resolveMediaUrl(item) : normalizeMediaPayload(item)
    ))
  }

  if (!value || typeof value !== 'object') {
    if (mediaKeys.has(key)) return resolveMediaUrl(value)
    if (key === 'url' && typeof value === 'string' && /^\/?api\/(?:uploads|demo)\//i.test(value)) {
      return resolveMediaUrl(value)
    }
    return value
  }

  return Object.fromEntries(
    Object.entries(value).map(([entryKey, entryValue]) => [
      entryKey,
      normalizeMediaPayload(entryValue, entryKey),
    ]),
  )
}
