<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, updateUserStatus } from '../../api/admin'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const users = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(20)
const keyword = ref('')
const roleFilter = ref('')
const statusFilter = ref('')
const errorMessage = ref('')
let requestSequence = 0

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
    errorMessage.value = error.message || '加载用户列表失败'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
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

const toggleStatus = async (user) => {
  if (user.id === auth.user?.userId) {
    ElMessage.warning('不能禁用当前登录账号')
    return
  }
  const newStatus = user.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const actionText = newStatus === 'ENABLED' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定${actionText}用户「${user.nickname}（${user.username}）」吗？`,
      '操作确认',
      {
        confirmButtonText: actionText,
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await updateUserStatus(user.id, newStatus)
    ElMessage.success(`${actionText}成功`)
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const statusTagType = (status) => (status === 'ENABLED' ? 'success' : 'danger')
const roleTagType = (role) => (role === 'ADMIN' ? 'primary' : 'info')

onMounted(loadUsers)
</script>

<template>
  <div class="admin-users">
    <div class="page-heading">
      <div>
        <p class="eyebrow">用户与权限</p>
        <h2>用户管理</h2>
      </div>
      <el-button :loading="loading" @click="loadUsers">刷新</el-button>
    </div>

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
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
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
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
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
        empty-text="暂无符合条件的用户"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="phone" label="手机号" min-width="140">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="180">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN') : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              :disabled="row.id !== auth.user?.userId"
              content="不能操作当前登录账号"
              placement="top"
            >
              <span>
                <el-button
                  :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                  :disabled="row.id === auth.user?.userId"
                  size="small"
                  plain
                  @click="toggleStatus(row)"
                >
                  {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
                </el-button>
              </span>
            </el-tooltip>
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
  </div>
</template>

<style scoped>
.admin-users {
  max-width: 1240px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #409eff;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h2 {
  margin: 0;
  color: #303133;
  font-size: 24px;
}

.search-card,
.error-alert {
  margin-bottom: 16px;
}

.keyword-input {
  width: 230px;
}

.filter-select {
  width: 130px;
}

.table-card {
  min-height: 400px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 900px) {
  .keyword-input,
  .filter-select {
    width: 180px;
  }
}
</style>
