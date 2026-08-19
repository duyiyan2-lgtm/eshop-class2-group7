import { computed, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { addCartItem } from '../api/cart'
import { getVehicleConfigurator, quoteVehicle } from '../api/vehicle'
import { useAuthStore } from '../stores/auth'
import { notifyCartUpdated } from '../utils/cartBadge'
import { PANEL_TABS, vehicleFolder } from '../utils/vehicle'

export const useVehicleConfigurator = () => {
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()

  const loading = ref(false)
  const quoting = ref(false)
  const submitting = ref(false)
  const errorMessage = ref('')
  const configurator = ref(null)
  const selectedSkuId = ref()
  const selectedByGroup = ref({})
  const quote = ref(null)
  const activeTab = ref('COLOR')
  const drawerVisible = ref(false)

  let quoteTimer = 0
  let quoteAbort
  let quoteToken = 0
  let loadToken = 0

  const isMobile = computed(() => route.path.startsWith('/m'))
  const productId = computed(() => Number(route.params.id))
  const folder = computed(() => vehicleFolder(configurator.value))
  const selectedSku = computed(() => (
    configurator.value?.skus.find((sku) => sku.id === selectedSkuId.value) || null
  ))
  const groupByCode = (code) => (
    configurator.value?.groups.find((item) => item.code === code) || null
  )

  const selectedColor = computed(() => {
    const group = groupByCode('COLOR')
    const valueId = group ? selectedByGroup.value[group.id] : null
    return group?.values.find((item) => item.id === valueId) || group?.values[0] || null
  })

  const selectedWheel = computed(() => {
    const group = groupByCode('WHEEL')
    const valueId = group ? selectedByGroup.value[group.id] : null
    return group?.values.find((item) => item.id === valueId) || group?.values[0] || null
  })

  const selectedInterior = computed(() => {
    const group = groupByCode('INTERIOR')
    const valueId = group ? selectedByGroup.value[group.id] : null
    return group?.values.find((item) => item.id === valueId) || group?.values[0] || null
  })

  const selectedPacks = computed(() => {
    const group = groupByCode('PACK')
    const ids = group ? (selectedByGroup.value[group.id] || []) : []
    return (group?.values || []).filter((item) => ids.includes(item.id))
  })
  const optionValueIds = computed(() => {
    const ids = []
    Object.values(selectedByGroup.value).forEach((value) => {
      if (Array.isArray(value)) ids.push(...value)
      else if (value) ids.push(value)
    })
    return [...ids].sort((a, b) => a - b)
  })
  const tabIndex = computed(() => (
    Math.max(0, PANEL_TABS.findIndex((tab) => tab.code === activeTab.value))
  ))
  const isLastTab = computed(() => tabIndex.value >= PANEL_TABS.length - 1)
  const nextLabel = computed(() => {
    if (isLastTab.value) return '立即购买'
    const next = PANEL_TABS[tabIndex.value + 1]
    return next ? `下一步：${next.label}` : '下一步'
  })
  const canPurchase = computed(() => Boolean(quote.value?.purchasable) && !quoting.value)

  const listRouteName = computed(() => (isMobile.value ? 'mobile-vehicles' : 'pc-vehicles'))
  const loginRouteName = computed(() => (isMobile.value ? 'mobile-login' : 'pc-login'))
  const checkoutRouteName = computed(() => (isMobile.value ? 'mobile-checkout' : 'pc-checkout'))

  const ruleOf = (skuId, valueId) => (
    configurator.value?.rules.find((rule) => rule.skuId === skuId && rule.optionValueId === valueId) || null
  )

  const isOptionAvailable = (valueId) => {
    const rule = ruleOf(selectedSkuId.value, valueId)
    return !rule || rule.available
  }

  const disabledReason = (value) => (
    isOptionAvailable(value.id) ? '' : '当前版本不提供该选项'
  )

  const collectDefaultSelections = (data, skuId) => {
    const next = {}
    data.groups.forEach((group) => {
      const available = group.values.filter((value) => {
        const rule = data.rules.find((item) => item.skuId === skuId && item.optionValueId === value.id)
        return !rule || rule.available
      })
      const included = available.find((value) => {
        const rule = data.rules.find((item) => item.skuId === skuId && item.optionValueId === value.id)
        return rule?.included
      })
      if (group.selectionType === 'MULTI') {
        next[group.id] = included ? [included.id] : []
        return
      }
      const preferred = data.defaultOptionValueIds?.find((id) => available.some((value) => value.id === id))
      next[group.id] = preferred || included?.id || available[0]?.id || null
    })
    return next
  }

  const applyDefaults = (data) => {
    selectedSkuId.value = data.defaultSkuId
    selectedByGroup.value = collectDefaultSelections(data, data.defaultSkuId)
    activeTab.value = 'COLOR'
  }

  const loadConfigurator = async () => {
    const token = ++loadToken
    loading.value = true
    errorMessage.value = ''
    configurator.value = null
    quote.value = null
    try {
      if (!Number.isInteger(productId.value) || productId.value <= 0) {
        throw new Error('车型编号不正确')
      }
      const data = await getVehicleConfigurator(productId.value)
      if (token !== loadToken) return
      configurator.value = data
      applyDefaults(data)
    } catch (error) {
      if (token !== loadToken) return
      errorMessage.value = error.message || '选配器加载失败'
    } finally {
      if (token === loadToken) loading.value = false
    }
  }

  const runQuote = async () => {
    if (!configurator.value || !selectedSkuId.value) return
    quoteAbort?.abort()
    const controller = new AbortController()
    quoteAbort = controller
    const token = ++quoteToken
    quoting.value = true
    try {
      const data = await quoteVehicle(productId.value, {
        skuId: selectedSkuId.value,
        optionValueIds: optionValueIds.value,
      }, { signal: controller.signal })
      if (token !== quoteToken) return
      quote.value = data
    } catch (error) {
      if (error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError' || controller.signal.aborted) return
      if (token !== quoteToken) return
      quote.value = null
      ElMessage.error(error.message || '报价失败，请调整配置后重试')
    } finally {
      if (token === quoteToken) quoting.value = false
    }
  }

  const scheduleQuote = () => {
    window.clearTimeout(quoteTimer)
    quoteTimer = window.setTimeout(runQuote, 140)
  }

  const selectSku = (sku) => {
    if (!sku || selectedSkuId.value === sku.id) return
    selectedSkuId.value = sku.id
    selectedByGroup.value = collectDefaultSelections(configurator.value, sku.id)
    activeTab.value = 'COLOR'
  }

  const selectSingle = (group, value) => {
    if (!isOptionAvailable(value.id)) return
    selectedByGroup.value = { ...selectedByGroup.value, [group.id]: value.id }
    activeTab.value = group.code
  }

  const toggleMulti = (group, value) => {
    if (!isOptionAvailable(value.id)) return
    const current = Array.isArray(selectedByGroup.value[group.id]) ? [...selectedByGroup.value[group.id]] : []
    const index = current.indexOf(value.id)
    if (index >= 0) current.splice(index, 1)
    else current.push(value.id)
    selectedByGroup.value = { ...selectedByGroup.value, [group.id]: current }
    activeTab.value = group.code
  }

  const goNext = () => {
    if (isLastTab.value) {
      drawerVisible.value = true
      return
    }
    activeTab.value = PANEL_TABS[tabIndex.value + 1].code
  }

  const resetConfig = () => {
    if (!configurator.value) return
    applyDefaults(configurator.value)
    drawerVisible.value = false
  }

  const requireLogin = async () => {
    if (auth.isLoggedIn) return false
    ElMessage.info('请先登录后再购买课程演示车型')
    await router.push({
      name: loginRouteName.value,
      query: { redirect: route.fullPath },
    })
    return true
  }

  const addConfiguredVehicle = async () => {
    if (await requireLogin()) return false
    if (!canPurchase.value) {
      ElMessage.warning(quote.value?.unavailableReason || '当前配置暂不可购买')
      return false
    }
    submitting.value = true
    try {
      const item = await addCartItem({
        skuId: selectedSkuId.value,
        quantity: 1,
        optionValueIds: optionValueIds.value,
      })
      notifyCartUpdated()
      ElMessage.success(`已加入购物车，当前数量 ${item.quantity}`)
      return true
    } catch (error) {
      ElMessage.error(error.message || '加入购物车失败')
      return false
    } finally {
      submitting.value = false
    }
  }

  const buyNow = async () => {
    const added = await addConfiguredVehicle()
    if (added) await router.push({ name: checkoutRouteName.value })
  }

  watch(optionValueIds, scheduleQuote)
  watch(selectedSkuId, scheduleQuote)
  watch(() => route.params.id, loadConfigurator, { immediate: true })

  onUnmounted(() => {
    loadToken += 1
    quoteToken += 1
    window.clearTimeout(quoteTimer)
    quoteAbort?.abort()
  })

  return {
    loading,
    quoting,
    submitting,
    errorMessage,
    configurator,
    selectedSkuId,
    selectedByGroup,
    quote,
    activeTab,
    drawerVisible,
    folder,
    selectedSku,
    selectedColor,
    selectedWheel,
    selectedInterior,
    selectedPacks,
    optionValueIds,
    isLastTab,
    nextLabel,
    canPurchase,
    listRouteName,
    isOptionAvailable,
    disabledReason,
    loadConfigurator,
    selectSku,
    selectSingle,
    toggleMulti,
    goNext,
    resetConfig,
    addConfiguredVehicle,
    buyNow,
  }
}
