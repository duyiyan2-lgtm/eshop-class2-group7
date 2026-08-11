<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'
import {
  createAddress,
  getAddresses,
  removeAddress,
  setDefaultAddress,
  updateAddress,
} from '../../api/address'

const route = useRoute()
const router = useRouter()
const addresses = ref([])
const loading = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
const showForm = ref(false)
const editingId = ref(null)
const saving = ref(false)
const updatingId = ref(null)

const blankForm = () => ({
  receiverName: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false,
})

const form = reactive(blankForm())
const formTitle = computed(() => (editingId.value ? '编辑收货地址' : '新增收货地址'))
const returnPath = computed(() => {
  const raw = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect
  if (typeof raw !== 'string') return ''
  return /^\/m\/checkout(?:[/?#]|$)/.test(raw) ? raw : ''
})

const returnToCheckout = () => {
  if (returnPath.value) router.replace(returnPath.value)
}

const loadAddresses = async () => {
  if (loading.value) return false
  loading.value = true
  errorMessage.value = ''
  try {
    addresses.value = await getAddresses()
    return true
  } catch (error) {
    errorMessage.value = error.message || '地址加载失败'
    return false
  } finally {
    loading.value = false
  }
}

const onRefresh = async () => {
  refreshing.value = true
  const succeeded = await loadAddresses()
  refreshing.value = false
  showToast(succeeded ? '已刷新' : { type: 'fail', message: '刷新失败，请重试' })
}

const resetForm = () => {
  Object.assign(form, blankForm())
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  form.isDefault = addresses.value.length === 0
  showForm.value = true
}

const openEdit = (address) => {
  editingId.value = address.id
  Object.assign(form, {
    receiverName: address.receiverName,
    phone: address.phone,
    province: address.province,
    city: address.city,
    district: address.district,
    detail: address.detail,
    isDefault: address.isDefault,
  })
  showForm.value = true
}

const validateForm = () => {
  const fields = [
    { key: 'receiverName', message: '请填写收货人' },
    { key: 'phone', message: '请填写联系电话' },
    { key: 'province', message: '请填写省份' },
    { key: 'city', message: '请填写城市' },
    { key: 'district', message: '请填写区县' },
    { key: 'detail', message: '请填写详细地址' },
  ]
  for (const field of fields) {
    if (!String(form[field.key]).trim()) {
      showToast({ type: 'fail', message: field.message })
      return false
    }
  }
  if (!/^[0-9+\- ]{6,20}$/.test(form.phone.trim())) {
    showToast({ type: 'fail', message: '联系电话格式不正确' })
    return false
  }
  return true
}

const saveAddress = async () => {
  if (saving.value || !validateForm()) return false
  saving.value = true
  const payload = {
    receiverName: form.receiverName.trim(),
    phone: form.phone.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    detail: form.detail.trim(),
    isDefault: form.isDefault,
  }
  try {
    if (editingId.value) {
      await updateAddress(editingId.value, payload)
      showToast('地址已更新')
    } else {
      await createAddress(payload)
      showToast('地址已新增')
    }
    await loadAddresses()
    if (returnPath.value) {
      showToast('地址已保存，正在返回订单确认')
      await router.replace(returnPath.value)
    }
    return true
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '保存失败' })
    return false
  } finally {
    saving.value = false
  }
}

const beforeFormClose = (action) => {
  if (action !== 'confirm') return true
  return saveAddress()
}

const makeDefault = async (address) => {
  if (address.isDefault || updatingId.value) return
  updatingId.value = address.id
  try {
    await setDefaultAddress(address.id)
    showToast('已设为默认地址')
    await loadAddresses()
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '设置默认地址失败' })
  } finally {
    updatingId.value = null
  }
}

const deleteAddress = async (address) => {
  if (updatingId.value) return
  try {
    await showConfirmDialog({
      title: '删除地址',
      message: `确定删除 ${address.receiverName} 的收货地址吗？`,
      confirmButtonText: '删除',
      cancelButtonText: '保留',
    })
    updatingId.value = address.id
    await removeAddress(address.id)
    showToast('地址已删除')
    await loadAddresses()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      showToast({ type: 'fail', message: error.message || '删除失败' })
    }
  } finally {
    updatingId.value = null
  }
}

onMounted(loadAddresses)
</script>

<template>
  <section class="mobile-addresses">
    <div v-if="returnPath" class="checkout-return-card">
      <div>
        <strong>正在为本次订单选择地址</strong>
        <span>管理完成后可返回订单确认页继续结算</span>
      </div>
      <van-button size="small" type="primary" plain @click="returnToCheckout">
        返回结算
      </van-button>
    </div>

    <van-notice-bar
      v-if="errorMessage"
      color="#dc2626"
      background="#fef2f2"
      left-icon="warning-o"
    >
      <span>{{ errorMessage }}</span>
      <button class="notice-action" type="button" @click="loadAddresses">重试</button>
    </van-notice-bar>

    <div v-if="loading && !addresses.length" class="address-loading">
      <van-loading size="24px">加载中...</van-loading>
    </div>

    <van-empty
      v-else-if="!addresses.length && !loading"
      description="还没有收货地址"
    >
      <van-button round type="primary" size="small" @click="openCreate">
        新增地址
      </van-button>
    </van-empty>

    <template v-else>
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <div class="address-list">
          <article
            v-for="address in addresses"
            :key="address.id"
            class="address-card"
            :class="{ 'is-default': address.isDefault }"
          >
            <div class="address-header">
              <div class="address-avatar">{{ address.receiverName.slice(0, 1) }}</div>
              <div class="address-user">
                <span class="address-name">{{ address.receiverName }}</span>
                <span class="address-phone">{{ address.phone }}</span>
              </div>
              <van-tag v-if="address.isDefault" type="primary" size="mini">默认</van-tag>
            </div>

            <div class="address-detail">
              {{ address.province }} {{ address.city }} {{ address.district }} {{ address.detail }}
            </div>

            <div class="address-actions">
              <van-button
                v-if="!address.isDefault"
                size="small"
                plain
                hairline
                type="primary"
                :loading="updatingId === address.id"
                :disabled="Boolean(updatingId)"
                @click="makeDefault(address)"
              >
                设为默认
              </van-button>
              <span v-else class="default-hint">下单时优先使用</span>

              <div class="address-options">
                <van-button
                  size="small"
                  plain
                  hairline
                  :disabled="Boolean(updatingId)"
                  @click="openEdit(address)"
                >
                  编辑
                </van-button>
                <van-button
                  size="small"
                  plain
                  hairline
                  type="danger"
                  :loading="updatingId === address.id"
                  :disabled="Boolean(updatingId)"
                  @click="deleteAddress(address)"
                >
                  删除
                </van-button>
              </div>
            </div>
          </article>
        </div>

        <div class="add-address-wrap">
          <van-button
            round
            block
            type="primary"
            plain
            icon="plus"
            :disabled="Boolean(updatingId)"
            @click="openCreate"
          >
            新增收货地址
          </van-button>
        </div>
      </van-pull-refresh>
    </template>

    <van-dialog
      v-model:show="showForm"
      :title="formTitle"
      show-cancel-button
      :confirm-button-loading="saving"
      :before-close="beforeFormClose"
      close-on-click-overlay
    >
      <div class="form-body">
        <van-field
          v-model="form.receiverName"
          label="收货人"
          placeholder="请输入姓名"
          maxlength="50"
          clearable
        />
        <van-field
          v-model="form.phone"
          label="联系电话"
          placeholder="手机或固定电话"
          maxlength="20"
          clearable
        />
        <van-field
          v-model="form.province"
          label="省份"
          placeholder="如：广东省"
          maxlength="50"
          clearable
        />
        <van-field
          v-model="form.city"
          label="城市"
          placeholder="如：广州市"
          maxlength="50"
          clearable
        />
        <van-field
          v-model="form.district"
          label="区县"
          placeholder="如：天河区"
          maxlength="50"
          clearable
        />
        <van-field
          v-model="form.detail"
          label="详细地址"
          type="textarea"
          placeholder="街道、门牌号、楼栋和房间号"
          maxlength="255"
          show-word-limit
          rows="3"
          autosize
        />
        <van-checkbox v-model="form.isDefault" shape="square" class="form-default">
          设为默认收货地址
        </van-checkbox>
      </div>
    </van-dialog>
  </section>
</template>

<style scoped>
.mobile-addresses {
  min-height: 100%;
  padding-bottom: 30px;
  background: #f7f8fa;
}

.checkout-return-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px;
  padding: 14px 15px;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  background: #eff6ff;
}

.checkout-return-card > div {
  display: grid;
  gap: 4px;
}

.checkout-return-card strong { color: #1e3a8a; font-size: 14px; }
.checkout-return-card span { color: #64748b; font-size: 12px; line-height: 1.45; }

.notice-action {
  margin-left: 8px;
  padding: 0;
  border: 0;
  color: #2563eb;
  background: transparent;
}

.address-loading {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.address-list {
  padding: 12px 12px 0;
}

.address-card {
  margin-bottom: 10px;
  padding: 16px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
}

.address-card.is-default {
  border-color: #2563eb;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.1);
}

.address-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.address-avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  border-radius: 50%;
}

.address-user {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.address-name {
  color: #323233;
  font-size: 15px;
  font-weight: 600;
}

.address-phone {
  color: #969799;
  font-size: 13px;
}

.address-detail {
  margin: 12px 0;
  color: #555;
  font-size: 13px;
  line-height: 1.6;
}

.address-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

.default-hint {
  color: #969799;
  font-size: 12px;
}

.address-options {
  display: flex;
  gap: 8px;
}

.add-address-wrap {
  padding: 4px 12px 20px;
}

.form-body {
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px 16px 4px;
}

.form-default {
  padding: 12px 16px;
}
</style>
