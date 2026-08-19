export const VEHICLE_DISCLAIMER = '非小米汽车官方页面，仅用于课程项目演示'

export const VEHICLE_VIEWS = [
  { id: 'front45', label: '前 45°', file: 'front45.webp' },
  { id: 'side', label: '侧面', file: 'side.webp' },
  { id: 'rear45', label: '后 45°', file: 'rear45.webp' },
  { id: 'rear', label: '正后', file: 'rear.webp' },
  { id: 'front', label: '正前', file: 'front.webp' },
]

export const VEHICLE_COLOR_FOLDERS = {
  WHITE: 'white',
  BLACK: 'black',
  SILVER: 'silver',
  GREEN: 'green',
}

export const VEHICLE_COLOR_MATERIAL = {
  WHITE: '金属漆',
  BLACK: '金属漆',
  SILVER: '金属漆',
  GREEN: '金属漆',
}

export const PANEL_TABS = [
  { code: 'COLOR', label: '车身' },
  { code: 'WHEEL', label: '车轮' },
  { code: 'INTERIOR', label: '内饰' },
  { code: 'PACK', label: '选装' },
]

export const isVehicleProduct = (product) => product?.productKind === 'VEHICLE'

export const isVehicleLine = (item) => Boolean(item?.configurationSummary)

export const vehicleFolder = (product) => {
  const name = `${product?.name || product?.productName || ''}`
  if (name.includes('Ultra')) return 'ultra'
  if (name.includes('YU7')) return 'yu7'
  return 'su7'
}

const colorFolder = (colorCode) => {
  const mapped = VEHICLE_COLOR_FOLDERS[colorCode]
  if (mapped) return mapped
  const raw = String(colorCode || '').trim().toLowerCase()
  return raw || ''
}

export const vehicleImageSrc = (folder, colorCode, viewId) => {
  const view = VEHICLE_VIEWS.find((item) => item.id === viewId)
  const color = colorFolder(colorCode)
  if (!folder || !color || !view) return ''
  return `/product-images/vehicles/${folder}/${color}/${view.file}`
}

export const vehicleImageCandidates = (folder, colorCode, viewId) => (
  [vehicleImageSrc(folder, colorCode, viewId)]
)

export const probeVehicleImage = (src) => new Promise((resolve) => {
  if (!src) {
    resolve(false)
    return
  }
  const image = new Image()
  image.decoding = 'async'
  image.onload = () => resolve(true)
  image.onerror = () => resolve(false)
  image.src = src
})

export const vehicleColorThumb = (folder, colorCode) => (
  vehicleImageSrc(folder, colorCode, 'front45')
)

export const vehicleOptionThumb = (folder, groupCode, valueCode) => {
  if (groupCode === 'COLOR') return vehicleColorThumb(folder, valueCode)
  if (groupCode === 'WHEEL') return `/product-images/vehicles/shared/wheels/${valueCode.toLowerCase()}.webp`
  if (groupCode === 'INTERIOR') return `/product-images/vehicles/shared/interiors/${valueCode.toLowerCase()}.webp`
  if (groupCode === 'PACK') return `/product-images/vehicles/shared/packs/${valueCode.toLowerCase()}.webp`
  return ''
}

export const vehiclePlaceholderSrc = '/product-images/vehicles/placeholder.svg'

export const vehicleCoverSrc = (product) => (
  `/product-images/vehicles/${vehicleFolder(product)}/white/front45.webp`
)

export const isMobileShopPath = () => (
  typeof window !== 'undefined' && window.location.pathname.startsWith('/m')
)

export const vehicleProductPath = (productId) => (
  isMobileShopPath()
    ? `/m/vehicles/${productId}/configurator`
    : `/pc/vehicles/${productId}/configurator`
)

export const productBrowsePath = (item) => {
  const id = item.productId || item.id
  if (isVehicleLine(item) || isVehicleProduct(item)) return vehicleProductPath(id)
  return isMobileShopPath() ? `/m/products/${id}` : `/pc/products/${id}`
}

export const lineSpecText = (item, fallbackText) => (
  item?.configurationSummary || fallbackText || '默认规格'
)
