<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createManagedUser,
  getUsers,
  getUserSummary,
  updateUserRole,
  updateUserStatus,
} from '../../api/admin'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const summaryLoading = ref(false)
const users = ref([])
const summary = ref(null)
const total = ref(0)
const current = ref(1)
const size = ref(20)
const keyword = ref('')
const roleFilter = ref('')
const statusFilter = ref('')
const errorMessage = ref('')
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref()
let requestSequence = 0

const createForm = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  role: 'SELLER',
  status: 'ENABLED',
})

const createRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度应为 3 到 50 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 72, message: '密码长度应为 6 到 72 位', trigger: 'blur' },
  ],
  nickname: [
    { required: true, message: '请输入昵称或店铺联系人', trigger: 'blur' },
    { max: 50, message: '昵称不能超过 50 位', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择账号角色', trigger: 'change' }],
}

const loadSummary = async () => {
  summaryLoading.value = true
  try {
    summary.value = await getUserSummary()
  } catch {
    summary.value = null
  } finally {
    summaryLoading.value = false
  }
}

const loadUsers = async () => {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getUsers({
      current: current.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      role: roleFilter.value || undefined,
      status: statusFilter.value || undefined,
    })
    if (sequence !== requestSequence) return
    users.value = result.records || []
    total.value = Number(result.total) || 0
  } catch (error) {
    if (sequence !== requestSequence) return
    users.value = []
    total.value = 0
    errorMessage.value = error.message || '加载账号列表失败'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

const refreshAll = () => {
  loadUsers()
  loadSummary()
}

const onSearch = () => {
  current.value = 1
  loadUsers()
}

const resetSearch = () => {
  keyword.value = ''
  roleFilter.value = ''
  statusFilter.value = ''
  onSearch()
}

const onPageChange = (page) => {
  current.value = page
  loadUsers()
}

const onSizeChange = (pageSize) => {
  size.value = pageSize
  current.value = 1
  loadUsers()
}

const resetCreateForm = (role = 'SELLER') => {
  Object.assign(createForm, {
    username: '',
    password: '',
    nickname: '',
    phone: '',
    role,
    status: 'ENABLED',
  })
  createFormRef.value?.clearValidate()
}

const openCreateDialog = (role = 'SELLER') => {
  resetCreateForm(role)
  createDialogVisible.value = true
}

const submitCreate = async () => {
  await createFormRef.value?.validate()
  createSubmitting.value = true
  try {
    await createManagedUser({
      username: createForm.username.trim(),
      password: createForm.password,
      nickname: createForm.nickname.trim(),
      phone: createForm.phone.trim() || undefined,
      role: createForm.role,
      status: createForm.status,
    })
    ElMessage.success(createForm.role === 'SELLER' ? '卖家账号创建成功' : '买家账号创建成功')
    createDialogVisible.value = false
    current.value = 1
    refreshAll()
  } catch (error) {
    ElMessage.error(error.message || '创建账号失败')
  } finally {
    createSubmitting.value = false
  }
}

const toggleStatus = async (user) => {
  if (user.id === auth.user?.userId || user.role === 'ADMIN') {
    ElMessage.warning('平台管理员账号不能在这里禁用')
    return
  }
  const newStatus = user.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const actionText = newStatus === 'ENABLED' && user.role === 'SELLER'
    ? '审核通过并启用'
    : newStatus === 'ENABLED' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定${actionText}「${user.nickname}（${user.username}）」吗？`,
      '账号状态确认',
      {
        confirmButtonText: actionText,
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await updateUserStatus(user.id, newStatus)
    ElMessage.success(`${actionText}成功`)
    refreshAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const toggleRole = async (user) => {
  if (user.role === 'ADMIN') return
  const newRole = user.role === 'SELLER' ? 'USER' : 'SELLER'
  const newRoleLabel = newRole === 'SELLER' ? '卖家' : '买家'
  try {
    await ElMessageBox.confirm(
      `确定将「${user.nickname}（${user.username}）」调整为${newRoleLabel}吗？角色变化会立即影响该账号的后台权限。`,
      '账号角色确认',
      {
        confirmButtonText: `设为${newRoleLabel}`,
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await updateUserRole(user.id, newRole)
    ElMessage.success(`已调整为${newRoleLabel}`)
    refreshAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '角色调整失败')
    }
  }
}

const statusTagType = (status) => (status === 'ENABLED' ? 'success' : 'danger')
const roleTagType = (role) => ({ ADMIN: 'primary', SELLER: 'warning', USER: 'info' })[role] || 'info'
const roleLabel = (role) => ({ ADMIN: '平台管理员', SELLER: '卖家', USER: '买家' })[role] || role
const statusLabel = (user) => {
  if (user.status === 'ENABLED') return '已启用'
  return user.role === 'SELLER' ? '待审核 / 已停用' : '已禁用'
}

onMounted(refreshAll)
</script>

<template>
  <div class="admin-users">
    <header class="account-hero">
      <div>
        <p>PLATFORM ACCOUNT CENTER</p>
        <h1>买家与卖家管理</h1>
        <span>统一创建和管理商城消费者与商家运营账号，角色和启停状态修改后立即生效。</span>
      </div>
      <div class="hero-actions">
        <el-button @click="openCreateDialog('USER')">新增买家</el-button>
        <el-button type="primary" @click="openCreateDialog('SELLER')">新增卖家</el-button>
      </div>
    </header>

    <section v-loading="summaryLoading" class="summary-grid">
      <article>
        <span>受管账号</span>
        <strong>{{ summary?.managedAccountCount ?? 0 }}</strong>
        <small>不含平台管理员</small>
      </article>
      <article class="buyer-card">
        <span>买家</span>
        <strong>{{ summary?.buyerCount ?? 0 }}</strong>
        <small>可登录 PC 与手机商城</small>
      </article>
      <article class="seller-card">
        <span>卖家</span>
        <strong>{{ summary?.sellerCount ?? 0 }}</strong>
        <small>可进入商家工作台</small>
      </article>
      <article class="enabled-card">
        <span>启用账号</span>
        <strong>{{ summary?.enabledCount ?? 0 }}</strong>
        <small>当前可以正常登录</small>
      </article>
      <article class="disabled-card">
        <span>待审核 / 禁用</span>
        <strong>{{ summary?.disabledCount ?? 0 }}</strong>
        <small>含待审核商家，启用后方可登录</small>
      </article>
    </section>

    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent="onSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            placeholder="用户名 / 昵称 / 手机号"
            clearable
            class="keyword-input"
            @clear="onSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="roleFilter"
            placeholder="全部"
            clearable
            class="filter-select"
            @change="onSearch"
          >
            <el-option label="买家" value="USER" />
            <el-option label="卖家" value="SELLER" />
            <el-option label="平台管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="statusFilter"
            placeholder="全部"
            clearable
            class="filter-select"
            @change="onSearch"
          >
            <el-option label="启用" value="ENABLED" />
            <el-option label="待审核 / 禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button :loading="loading" @click="refreshAll">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="errorMessage"
      class="error-alert"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button link type="primary" @click="loadUsers">重新加载</el-button>
      </template>
    </el-alert>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="users"
        stripe
        empty-text="暂无符合条件的账号"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="phone" label="手机号" min-width="140">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="145">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN') : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="row.role !== 'ADMIN'">
              <el-button size="small" plain @click="toggleRole(row)">
                {{ row.role === 'SELLER' ? '改为买家' : '设为卖家' }}
              </el-button>
              <el-button
                :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                size="small"
                plain
                @click="toggleStatus(row)"
              >
                {{ row.status === 'ENABLED' ? '禁用' : row.role === 'SELLER' ? '审核 / 启用' : '启用' }}
              </el-button>
            </template>
            <span v-else class="protected-note">受保护账号</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="createDialogVisible"
      :title="createForm.role === 'SELLER' ? '新增卖家账号' : '新增买家账号'"
      width="520px"
      destroy-on-close
      @closed="resetCreateForm()"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="卖家可进入商家工作台；买家可使用 PC 和手机商城。平台管理员角色不能在此创建。"
      />
      <el-form
        ref="createFormRef"
        class="create-form"
        :model="createForm"
        :rules="createRules"
        label-width="92px"
      >
        <el-form-item label="账号角色" prop="role">
          <el-radio-group v-model="createForm.role">
            <el-radio-button value="USER">买家</el-radio-button>
            <el-radio-button value="SELLER">卖家</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="createForm.username" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input
            v-model="createForm.password"
            type="password"
            maxlength="72"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model.trim="createForm.nickname" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model.trim="createForm.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="初始状态">
          <el-radio-group v-model="createForm.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">
          创建账号
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-users {
  max-width: 1320px;
  margin: 0 auto;
}

.account-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  color: #fff;
  background:
    radial-gradient(circle at 85% 15%, rgba(45, 212, 191, 0.32), transparent 26%),
    linear-gradient(125deg, #0f172a, #164e63 62%, #0f766e);
  border-radius: 20px;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.16);
}

.account-hero p {
  margin: 0 0 8px;
  color: #99f6e4;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.account-hero h1 {
  margin: 0 0 9px;
  font-size: 30px;
}

.account-hero span {
  color: #cbd5e1;
}

.hero-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin: 18px 0;
}

.summary-grid article {
  padding: 20px;
  color: #2563eb;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-top: 4px solid currentColor;
  border-radius: 14px;
}

.summary-grid span,
.summary-grid small {
  display: block;
  color: #64748b;
}

.summary-grid strong {
  display: block;
  margin: 10px 0 8px;
  color: #0f172a;
  font-size: 28px;
}

.summary-grid small {
  font-size: 12px;
}

.summary-grid .buyer-card { color: #0ea5e9; }
.summary-grid .seller-card { color: #f59e0b; }
.summary-grid .enabled-card { color: #10b981; }
.summary-grid .disabled-card { color: #ef4444; }

.search-card,
.error-alert {
  margin-bottom: 16px;
}

.keyword-input {
  width: 230px;
}

.filter-select {
  width: 140px;
}

.table-card {
  min-height: 400px;
}

.protected-note {
  color: #94a3b8;
  font-size: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.create-form {
  margin-top: 20px;
}

@media (max-width: 1000px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .account-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .keyword-input,
  .filter-select {
    width: 180px;
  }
}
</style>
