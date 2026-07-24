<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAdminCategory,
  getAdminCategories,
  removeAdminCategory,
  updateAdminCategory,
  updateAdminCategoryStatus,
} from '../../api/adminCatalog'
import { formatDateTime } from '../../utils/shop'

const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const operatingId = ref()
const dialogVisible = ref(false)
const editingId = ref()

const blankForm = () => ({
  parentId: null,
  name: '',
  sortOrder: 0,
  status: 'ENABLED',
})
const form = reactive(blankForm())
const dialogTitle = computed(() => (editingId.value ? '编辑分类' : '新增分类'))
const enabledCount = computed(() => categories.value.filter((item) => item.status === 'ENABLED').length)
const parentOptions = computed(() => (
  categories.value.filter((item) => item.id !== editingId.value)
))
const categoryNameMap = computed(() => new Map(categories.value.map((item) => [item.id, item.name])))

const loadCategories = async () => {
  loading.value = true
  try {
    categories.value = await getAdminCategories()
  } catch (error) {
    ElMessage.error(error.message || '分类加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = undefined
  Object.assign(form, blankForm())
  dialogVisible.value = true
}

const openEdit = (category) => {
  editingId.value = category.id
  Object.assign(form, {
    parentId: category.parentId || null,
    name: category.name,
    sortOrder: category.sortOrder,
    status: category.status,
  })
  dialogVisible.value = true
}

const saveCategory = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  const payload = {
    parentId: form.parentId || null,
    name: form.name.trim(),
    sortOrder: Number(form.sortOrder) || 0,
    status: form.status,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateAdminCategory(editingId.value, payload)
      ElMessage.success('分类已更新')
    } else {
      await createAdminCategory(payload)
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    await loadCategories()
  } catch (error) {
    ElMessage.error(error.message || '分类保存失败')
  } finally {
    saving.value = false
  }
}

const changeStatus = async (category, enabled) => {
  operatingId.value = category.id
  try {
    await updateAdminCategoryStatus(category.id, enabled ? 'ENABLED' : 'DISABLED')
    ElMessage.success(enabled ? '分类已启用' : '分类已停用')
    await loadCategories()
  } catch (error) {
    ElMessage.error(error.message || '分类状态修改失败')
    await loadCategories()
  } finally {
    operatingId.value = undefined
  }
}

const deleteCategory = async (category) => {
  try {
    await ElMessageBox.confirm(
      `确定删除分类“${category.name}”吗？存在子分类或商品时将无法删除。`,
      '删除分类',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    operatingId.value = category.id
    await removeAdminCategory(category.id)
    ElMessage.success('分类已删除')
    await loadCategories()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '分类删除失败')
    }
  } finally {
    operatingId.value = undefined
  }
}

onMounted(loadCategories)
</script>

<template>
  <section class="admin-page">
    <header class="admin-page-heading">
      <div>
        <p>CATALOG MANAGEMENT</p>
        <h1>商品分类</h1>
        <span>分类停用前，需要先下架该分类中的在售商品。</span>
      </div>
      <el-button type="primary" size="large" @click="openCreate">新增分类</el-button>
    </header>

    <div class="summary-row">
      <div><span>分类总数</span><strong>{{ categories.length }}</strong></div>
      <div><span>启用分类</span><strong>{{ enabledCount }}</strong></div>
      <div><span>停用分类</span><strong>{{ categories.length - enabledCount }}</strong></div>
    </div>

    <section class="table-card">
      <div class="table-toolbar">
        <div>
          <h2>分类列表</h2>
          <span>按排序值升序展示</span>
        </div>
        <el-button :loading="loading" @click="loadCategories">刷新</el-button>
      </div>
      <el-table
        :data="categories"
        v-loading="loading"
        row-key="id"
        stripe
        empty-text="暂无分类"
      >
        <el-table-column prop="id" label="ID" width="76" />
        <el-table-column prop="name" label="分类名称" min-width="190">
          <template #default="{ row }">
            <div class="category-name">
              <b>{{ row.name }}</b>
              <small v-if="row.parentId">上级：{{ categoryNameMap.get(row.parentId) || row.parentId }}</small>
              <small v-else>一级分类</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'ENABLED'"
              :loading="operatingId === row.id"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="changeStatus(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="190">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="danger"
              :loading="operatingId === row.id"
              @click="deleteCategory(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="分类名称" required>
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" clearable placeholder="不选择则为一级分类" style="width: 100%">
            <el-option
              v-for="category in parentOptions"
              :key="category.id"
              :label="category.name"
              :value="category.id"
              :disabled="category.status !== 'ENABLED' && form.status === 'ENABLED'"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="排序值">
            <el-input-number v-model="form.sortOrder" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="分类状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCategory">保存分类</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-page { max-width: 1280px; margin: 0 auto; }
.admin-page-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 20px; }
.admin-page-heading p { margin: 0 0 6px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .13em; }
.admin-page-heading h1 { margin: 0; color: #0f172a; font-size: 30px; }
.admin-page-heading span { display: block; margin-top: 8px; color: #64748b; }
.summary-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }
.summary-row > div { display: flex; align-items: center; justify-content: space-between; padding: 18px 22px; background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; }
.summary-row span { color: #64748b; }
.summary-row strong { color: #1d4ed8; font-size: 24px; }
.table-card { overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 10px 30px rgba(15, 23, 42, .04); }
.table-toolbar { display: flex; align-items: center; justify-content: space-between; padding: 20px 22px; border-bottom: 1px solid #eef2f7; }
.table-toolbar h2 { margin: 0; color: #0f172a; font-size: 18px; }
.table-toolbar span { display: block; margin-top: 5px; color: #94a3b8; font-size: 12px; }
.category-name { display: grid; gap: 5px; }
.category-name b { color: #0f172a; }
.category-name small { color: #94a3b8; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
@media (max-width: 720px) {
  .admin-page-heading { align-items: stretch; flex-direction: column; gap: 16px; }
  .summary-row { grid-template-columns: 1fr; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
