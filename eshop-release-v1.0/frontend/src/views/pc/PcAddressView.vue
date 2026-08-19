<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref()
const errorMessage = ref('')

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
const dialogTitle = computed(() => (editingId.value ? '编辑收货地址' : '新增收货地址'))
const returnPath = computed(() => (
  typeof route.query.redirect === 'string'
  && route.query.redirect.startsWith('/')
  && !route.query.redirect.startsWith('//')
    ? route.query.redirect
    : ''
))

const loadAddresses = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    addresses.value = await getAddresses()
  } catch (error) {
    errorMessage.value = error.message || '地址加载失败'
  } finally {
    loading.value = false
  }
}

const resetForm = () => Object.assign(form, blankForm())

const openCreate = () => {
  editingId.value = undefined
  resetForm()
  form.isDefault = addresses.value.length === 0
  dialogVisible.value = true
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
  dialogVisible.value = true
}

const validateForm = () => {
  const required = [
    ['receiverName', '请填写收货人'],
    ['phone', '请填写联系电话'],
    ['province', '请填写省份'],
    ['city', '请填写城市'],
    ['district', '请填写区县'],
    ['detail', '请填写详细地址'],
  ]
  for (const [key, message] of required) {
    if (!String(form[key]).trim()) {
      ElMessage.warning(message)
      return false
    }
  }
  if (!/^[0-9+\- ]{6,20}$/.test(form.phone.trim())) {
    ElMessage.warning('联系电话格式不正确')
    return false
  }
  return true
}

const saveAddress = async () => {
  if (!validateForm()) return
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
      ElMessage.success('收货地址已更新')
    } else {
      await createAddress(payload)
      ElMessage.success('收货地址已新增')
    }
    dialogVisible.value = false
    await loadAddresses()
  } catch (error) {
    ElMessage.error(error.message || '地址保存失败')
  } finally {
    saving.value = false
  }
}

const makeDefault = async (address) => {
  if (address.isDefault) return
  try {
    await setDefaultAddress(address.id)
    ElMessage.success('已设为默认地址')
    await loadAddresses()
  } catch (error) {
    ElMessage.error(error.message || '设置默认地址失败')
  }
}

const deleteAddress = async (address) => {
  try {
    await ElMessageBox.confirm(
      `确定删除 ${address.receiverName} 的收货地址吗？`,
      '删除地址',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '保留' },
    )
    await removeAddress(address.id)
    ElMessage.success('收货地址已删除')
    await loadAddresses()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(loadAddresses)
</script>

<template>
  <div class="address-page">
    <header class="page-heading">
      <div>
        <h1>收货地址</h1>
        <span>管理下单时使用的联系人和配送地址</span>
      </div>
      <div class="heading-actions">
        <el-button v-if="returnPath" @click="router.push(returnPath)">返回订单确认</el-button>
        <el-button type="primary" @click="openCreate">新增地址</el-button>
      </div>
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="address-alert"
    />

    <section v-loading="loading" class="address-grid">
      <article
        v-for="address in addresses"
        :key="address.id"
        class="address-card"
        :class="{ default: address.isDefault }"
      >
        <div class="address-top">
          <div class="avatar">{{ address.receiverName.slice(0, 1) }}</div>
          <div>
            <h2>{{ address.receiverName }}</h2>
            <p>{{ address.phone }}</p>
          </div>
          <el-tag v-if="address.isDefault" type="primary">默认地址</el-tag>
        </div>
        <p class="full-address">
          {{ address.province }} {{ address.city }} {{ address.district }} {{ address.detail }}
        </p>
        <footer>
          <el-button v-if="!address.isDefault" link type="primary" @click="makeDefault(address)">
            设为默认
          </el-button>
          <span v-else>下单时优先使用</span>
          <div>
            <el-button link @click="openEdit(address)">编辑</el-button>
            <el-button link type="danger" @click="deleteAddress(address)">删除</el-button>
          </div>
        </footer>
      </article>

      <button v-if="addresses.length" class="add-card" type="button" @click="openCreate">
        <b>＋</b>
        <span>添加新的收货地址</span>
      </button>

      <el-empty v-if="!loading && !addresses.length" description="还没有收货地址">
        <el-button type="primary" @click="openCreate">新增第一个地址</el-button>
      </el-empty>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form label-position="top" class="address-form" @submit.prevent>
        <div class="two-columns">
          <el-form-item label="收货人" required>
            <el-input v-model="form.receiverName" maxlength="50" placeholder="请输入姓名" />
          </el-form-item>
          <el-form-item label="联系电话" required>
            <el-input v-model="form.phone" maxlength="20" placeholder="手机或固定电话" />
          </el-form-item>
        </div>
        <div class="three-columns">
          <el-form-item label="省份" required>
            <el-input v-model="form.province" maxlength="50" placeholder="如：广东省" />
          </el-form-item>
          <el-form-item label="城市" required>
            <el-input v-model="form.city" maxlength="50" placeholder="如：广州市" />
          </el-form-item>
          <el-form-item label="区县" required>
            <el-input v-model="form.district" maxlength="50" placeholder="如：天河区" />
          </el-form-item>
        </div>
        <el-form-item label="详细地址" required>
          <el-input
            v-model="form.detail"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="街道、门牌号、楼栋和房间号"
          />
        </el-form-item>
        <el-checkbox v-model="form.isDefault">设为默认收货地址</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAddress">保存地址</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.address-page { width: min(1180px, 100%); margin: 0 auto; padding: 16px 0 56px; }
.page-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 24px; }
.page-heading p { margin: 0 0 6px; color: #e1251b; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.page-heading h1 { margin: 0; color: #0f172a; font-size: 32px; }
.page-heading span { display: block; margin-top: 8px; color: #64748b; }
.heading-actions { display: flex; gap: 10px; }
.address-alert { margin-bottom: 18px; }
.address-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; min-height: 250px; }
.address-card, .add-card { min-height: 220px; padding: 26px; background: #fff; border: 1px solid #e2e8f0; border-radius: 18px; box-shadow: 0 12px 35px rgba(15, 23, 42, .05); }
.address-card.default { border-color: #ff8f1f; box-shadow: 0 12px 35px rgba(225, 37, 27, .10); }
.address-top { display: flex; align-items: center; gap: 14px; }
.address-top .avatar { display: grid; width: 44px; height: 44px; place-items: center; color: #fff; background: linear-gradient(135deg, #c8161d, #ff8f1f); border-radius: 50%; font-weight: 800; }
.address-top h2 { margin: 0; color: #0f172a; font-size: 18px; }
.address-top p { margin: 5px 0 0; color: #64748b; }
.address-top .el-tag { margin-left: auto; }
.full-address { min-height: 52px; margin: 24px 0; color: #334155; line-height: 1.75; }
.address-card footer { display: flex; align-items: center; justify-content: space-between; padding-top: 16px; border-top: 1px solid #eef2f7; }
.address-card footer > span { color: #94a3b8; font-size: 13px; }
.add-card { display: grid; place-content: center; gap: 10px; color: #64748b; border-style: dashed; cursor: pointer; font: inherit; }
.add-card:hover { color: #e1251b; background: #f8fbff; border-color: #ff8f1f; }
.add-card b { font-size: 32px; font-weight: 400; text-align: center; }
.two-columns, .three-columns { display: grid; gap: 14px; }
.two-columns { grid-template-columns: repeat(2, 1fr); }
.three-columns { grid-template-columns: repeat(3, 1fr); }
@media (max-width: 760px) {
  .address-grid, .two-columns, .three-columns { grid-template-columns: 1fr; }
  .page-heading { align-items: stretch; flex-direction: column; gap: 18px; }
}
</style>
