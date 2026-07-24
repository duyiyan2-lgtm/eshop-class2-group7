<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAdminProduct,
  createAdminSku,
  getAdminCategories,
  getAdminProduct,
  getAdminProducts,
  removeAdminProduct,
  removeAdminSku,
  updateAdminProduct,
  updateAdminProductStatus,
  updateAdminSku,
  uploadAdminImage,
} from '../../api/adminCatalog'
import { formatDateTime, formatMoney, specsText } from '../../utils/shop'

const PRODUCT_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  ON_SALE: { label: '在售', type: 'success' },
  OFF_SALE: { label: '已下架', type: 'warning' },
}

const categories = ref([])
const products = ref([])
const current = ref(1)
const size = 10
const total = ref(0)
const keyword = ref('')
const categoryId = ref()
const loading = ref(false)
const saving = ref(false)
const operatingId = ref()
const productDialogVisible = ref(false)
const editingProductId = ref()
const uploadingImage = ref(false)
const imageInput = ref()

const skuDrawerVisible = ref(false)
const skuProduct = ref(null)
const skuDialogVisible = ref(false)
const editingSkuId = ref()
const skuSaving = ref(false)
const skuOperatingId = ref()

const blankProduct = () => ({
  categoryId: undefined,
  name: '',
  subtitle: '',
  mainImage: '',
  detail: '',
  status: 'DRAFT',
})
const productForm = reactive(blankProduct())
const blankSku = () => ({
  skuCode: '',
  specsJson: '{"规格":"默认"}',
  price: 1,
  stock: 0,
  status: 'ENABLED',
})
const skuForm = reactive(blankSku())

const categoryMap = computed(() => new Map(categories.value.map((item) => [item.id, item.name])))
const enabledCategories = computed(() => categories.value.filter((item) => item.status === 'ENABLED'))
const productDialogTitle = computed(() => (editingProductId.value ? '编辑商品' : '新增商品'))
const skuDialogTitle = computed(() => (editingSkuId.value ? '编辑 SKU' : '新增 SKU'))

const productStatus = (status) => PRODUCT_STATUS[status] || { label: status, type: 'info' }

const loadCategories = async () => {
  categories.value = await getAdminCategories()
}

const loadProducts = async () => {
  loading.value = true
  try {
    const page = await getAdminProducts({
      current: current.value,
      size,
      categoryId: categoryId.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    products.value = page.records
    total.value = page.total
  } catch (error) {
    ElMessage.error(error.message || '商品加载失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  current.value = 1
  loadProducts()
}

const resetSearch = () => {
  keyword.value = ''
  categoryId.value = undefined
  current.value = 1
  loadProducts()
}

const changePage = (page) => {
  current.value = page
  loadProducts()
}

const openCreateProduct = () => {
  editingProductId.value = undefined
  Object.assign(productForm, blankProduct())
  productForm.categoryId = enabledCategories.value[0]?.id
  productDialogVisible.value = true
}

const openEditProduct = async (row) => {
  operatingId.value = row.id
  try {
    const detail = await getAdminProduct(row.id)
    editingProductId.value = row.id
    Object.assign(productForm, {
      categoryId: detail.categoryId,
      name: detail.name,
      subtitle: detail.subtitle || '',
      mainImage: detail.mainImage || '',
      detail: detail.detail || '',
      status: detail.status,
    })
    productDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '商品详情加载失败')
  } finally {
    operatingId.value = undefined
  }
}

const validateProduct = () => {
  if (!productForm.categoryId) {
    ElMessage.warning('请选择商品分类')
    return false
  }
  if (!productForm.name.trim()) {
    ElMessage.warning('请填写商品名称')
    return false
  }
  return true
}

const saveProduct = async () => {
  if (!validateProduct()) return
  const payload = {
    categoryId: productForm.categoryId,
    name: productForm.name.trim(),
    subtitle: productForm.subtitle.trim() || null,
    mainImage: productForm.mainImage.trim() || null,
    detail: productForm.detail.trim() || null,
    status: productForm.status,
  }
  saving.value = true
  try {
    let product
    if (editingProductId.value) {
      product = await updateAdminProduct(editingProductId.value, payload)
      ElMessage.success('商品信息已更新')
    } else {
      product = await createAdminProduct(payload)
      ElMessage.success('商品草稿已创建，请继续添加 SKU')
    }
    productDialogVisible.value = false
    await loadProducts()
    if (!editingProductId.value) await openSkuManager(product)
  } catch (error) {
    ElMessage.error(error.message || '商品保存失败')
  } finally {
    saving.value = false
  }
}

const selectImage = () => imageInput.value?.click()

const uploadImage = async (event) => {
  const [file] = event.target.files || []
  event.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  uploadingImage.value = true
  try {
    const result = await uploadAdminImage(file)
    productForm.mainImage = result.url
    ElMessage.success('商品图片上传成功')
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    uploadingImage.value = false
  }
}

const toggleProductStatus = async (row) => {
  const targetStatus = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  const action = targetStatus === 'ON_SALE' ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(
      `确定${action}商品“${row.name}”吗？`,
      `${action}商品`,
      { type: targetStatus === 'ON_SALE' ? 'success' : 'warning' },
    )
    operatingId.value = row.id
    await updateAdminProductStatus(row.id, targetStatus)
    ElMessage.success(`商品已${action}`)
    await loadProducts()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || `${action}失败`)
    }
  } finally {
    operatingId.value = undefined
  }
}

const deleteProduct = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定删除商品“${row.name}”吗？请先删除其 SKU，存在订单引用时不能删除。`,
      '删除商品',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    operatingId.value = row.id
    await removeAdminProduct(row.id)
    ElMessage.success('商品已删除')
    await loadProducts()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '商品删除失败')
    }
  } finally {
    operatingId.value = undefined
  }
}

const refreshSkuProduct = async () => {
  if (!skuProduct.value?.id) return
  skuProduct.value = await getAdminProduct(skuProduct.value.id)
}

const openSkuManager = async (row) => {
  operatingId.value = row.id
  try {
    skuProduct.value = await getAdminProduct(row.id)
    skuDrawerVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || 'SKU 加载失败')
  } finally {
    operatingId.value = undefined
  }
}

const openCreateSku = () => {
  editingSkuId.value = undefined
  Object.assign(skuForm, blankSku())
  skuForm.skuCode = `SKU-${skuProduct.value.id}-${Date.now().toString().slice(-6)}`
  skuDialogVisible.value = true
}

const openEditSku = (sku) => {
  editingSkuId.value = sku.id
  Object.assign(skuForm, {
    skuCode: sku.skuCode,
    specsJson: sku.specsJson,
    price: Number(sku.price),
    stock: sku.stock,
    status: sku.status,
  })
  skuDialogVisible.value = true
}

const validateSku = () => {
  if (!skuForm.skuCode.trim()) {
    ElMessage.warning('请填写 SKU 编码')
    return false
  }
  try {
    const specs = JSON.parse(skuForm.specsJson)
    if (!specs || Array.isArray(specs) || typeof specs !== 'object') throw new Error()
  } catch {
    ElMessage.warning('SKU 规格必须是合法的 JSON 对象')
    return false
  }
  if (Number(skuForm.price) <= 0) {
    ElMessage.warning('SKU 价格必须大于 0')
    return false
  }
  return true
}

const saveSku = async () => {
  if (!validateSku()) return
  const payload = {
    skuCode: skuForm.skuCode.trim(),
    specsJson: skuForm.specsJson.trim(),
    price: Number(skuForm.price),
    stock: Number(skuForm.stock),
    status: skuForm.status,
  }
  skuSaving.value = true
  try {
    if (editingSkuId.value) {
      await updateAdminSku(editingSkuId.value, payload)
      ElMessage.success('SKU 已更新')
    } else {
      await createAdminSku(skuProduct.value.id, payload)
      ElMessage.success('SKU 已创建')
    }
    skuDialogVisible.value = false
    await refreshSkuProduct()
    await loadProducts()
  } catch (error) {
    ElMessage.error(error.message || 'SKU 保存失败')
  } finally {
    skuSaving.value = false
  }
}

const deleteSku = async (sku) => {
  try {
    await ElMessageBox.confirm(
      `确定删除 SKU“${sku.skuCode}”吗？存在有效订单引用时无法删除。`,
      '删除 SKU',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    skuOperatingId.value = sku.id
    await removeAdminSku(sku.id)
    ElMessage.success('SKU 已删除')
    await refreshSkuProduct()
    await loadProducts()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || 'SKU 删除失败')
    }
  } finally {
    skuOperatingId.value = undefined
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadCategories(), loadProducts()])
  } catch (error) {
    ElMessage.error(error.message || '商品管理初始化失败')
  }
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page-heading">
      <div>
        <p>PRODUCT & SKU</p>
        <h1>商品与 SKU 管理</h1>
        <span>先创建商品草稿并配置至少一个启用的 SKU，再将商品上架。</span>
      </div>
      <el-button type="primary" size="large" @click="openCreateProduct">新增商品</el-button>
    </header>

    <section class="filter-card">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索商品名称"
        @keyup.enter="search"
      />
      <el-select v-model="categoryId" clearable placeholder="全部分类">
        <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetSearch">重置</el-button>
      <span>共 {{ total }} 件商品</span>
    </section>

    <section class="table-card">
      <el-table
        :data="products"
        v-loading="loading"
        stripe
        empty-text="暂无符合条件的商品"
      >
        <el-table-column label="商品" min-width="310">
          <template #default="{ row }">
            <div class="product-cell">
              <div class="product-image">
                <img v-if="row.mainImage" :src="row.mainImage" :alt="row.name" />
                <span v-else>E-Shop</span>
              </div>
              <div>
                <b>{{ row.name }}</b>
                <p>{{ row.subtitle || '暂无副标题' }}</p>
                <small>ID {{ row.id }} · {{ categoryMap.get(row.categoryId) || '未分类' }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最低价格" width="125">
          <template #default="{ row }">
            <strong class="price">{{ row.minPrice == null ? '未配置' : formatMoney(row.minPrice) }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="totalStock" label="总库存" width="100" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="productStatus(row.status).type">{{ productStatus(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="175">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="operatingId === row.id" @click="openSkuManager(row)">
              SKU
            </el-button>
            <el-button link @click="openEditProduct(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'ON_SALE' ? 'warning' : 'success'"
              :loading="operatingId === row.id"
              @click="toggleProductStatus(row)"
            >
              {{ row.status === 'ON_SALE' ? '下架' : '上架' }}
            </el-button>
            <el-button
              v-if="row.status !== 'ON_SALE'"
              link
              type="danger"
              :loading="operatingId === row.id"
              @click="deleteProduct(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :current-page="current"
          :page-size="size"
          :total="total"
          @current-change="changePage"
        />
      </div>
    </section>

    <el-dialog v-model="productDialogVisible" :title="productDialogTitle" width="720px" destroy-on-close>
      <el-form label-position="top" @submit.prevent>
        <div class="two-columns">
          <el-form-item label="商品分类" required>
            <el-select v-model="productForm.categoryId" placeholder="选择分类" style="width: 100%">
              <el-option
                v-for="item in enabledCategories"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="当前状态">
            <el-input :model-value="productStatus(productForm.status).label" disabled />
          </el-form-item>
        </div>
        <el-form-item label="商品名称" required>
          <el-input v-model="productForm.name" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="商品副标题">
          <el-input v-model="productForm.subtitle" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="商品主图">
          <div class="image-field">
            <el-input v-model="productForm.mainImage" maxlength="500" placeholder="/api/... 或 https://..." />
            <el-button :loading="uploadingImage" @click="selectImage">上传图片</el-button>
            <input ref="imageInput" hidden type="file" accept="image/*" @change="uploadImage" />
          </div>
          <div v-if="productForm.mainImage" class="image-preview">
            <img :src="productForm.mainImage" alt="商品主图预览" />
          </div>
        </el-form-item>
        <el-form-item label="商品详情">
          <el-input v-model="productForm.detail" type="textarea" :rows="5" placeholder="输入商品介绍" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProduct">保存商品</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="skuDrawerVisible" size="780px" destroy-on-close>
      <template #header>
        <div class="drawer-heading">
          <div>
            <p>SKU MANAGEMENT</p>
            <h2>{{ skuProduct?.name }}</h2>
          </div>
          <el-button type="primary" @click="openCreateSku">新增 SKU</el-button>
        </div>
      </template>
      <el-alert
        title="在售商品至少需要保留一个启用的 SKU；有效订单引用的 SKU 不能删除。"
        type="info"
        show-icon
        :closable="false"
        class="sku-alert"
      />
      <el-table
        :data="skuProduct?.skus || []"
        border
        empty-text="还没有 SKU，请先新增规格"
      >
        <el-table-column prop="skuCode" label="SKU 编码" min-width="150" />
        <el-table-column label="规格" min-width="185">
          <template #default="{ row }">{{ specsText(row.specsJson) }}</template>
        </el-table-column>
        <el-table-column label="价格" width="105">
          <template #default="{ row }"><b class="price">{{ formatMoney(row.price) }}</b></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="125" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditSku(row)">编辑</el-button>
            <el-button
              link
              type="danger"
              :loading="skuOperatingId === row.id"
              @click="deleteSku(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="skuDialogVisible" :title="skuDialogTitle" width="570px" append-to-body>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="SKU 编码" required>
          <el-input v-model="skuForm.skuCode" maxlength="64" />
        </el-form-item>
        <el-form-item label="规格 JSON" required>
          <el-input
            v-model="skuForm.specsJson"
            type="textarea"
            :rows="3"
            maxlength="1000"
            placeholder='例如：{"颜色":"黑色","容量":"256GB"}'
          />
        </el-form-item>
        <div class="three-columns">
          <el-form-item label="价格" required>
            <el-input-number v-model="skuForm.price" :min="0.01" :precision="2" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="库存" required>
            <el-input-number v-model="skuForm.stock" :min="0" :max="999999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="skuForm.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="skuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="skuSaving" @click="saveSku">保存 SKU</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-page { max-width: 1380px; margin: 0 auto; }
.admin-page-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 20px; }
.admin-page-heading p { margin: 0 0 6px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .13em; }
.admin-page-heading h1 { margin: 0; color: #0f172a; font-size: 30px; }
.admin-page-heading span { display: block; margin-top: 8px; color: #64748b; }
.filter-card { display: grid; grid-template-columns: minmax(220px, 1fr) 220px auto auto 1fr; gap: 10px; align-items: center; margin-bottom: 16px; padding: 16px; background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; }
.filter-card > span { justify-self: end; color: #64748b; font-size: 13px; }
.table-card { overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 10px 30px rgba(15, 23, 42, .04); }
.product-cell { display: flex; align-items: center; min-width: 0; gap: 13px; padding: 5px 0; }
.product-image { display: grid; flex: 0 0 66px; width: 66px; height: 66px; place-items: center; overflow: hidden; color: #93c5fd; background: #eff6ff; border-radius: 10px; font-size: 11px; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-cell > div:last-child { min-width: 0; }
.product-cell b { display: block; overflow: hidden; color: #0f172a; text-overflow: ellipsis; white-space: nowrap; }
.product-cell p { margin: 5px 0; overflow: hidden; color: #64748b; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.product-cell small { color: #94a3b8; }
.price { color: #dc2626; }
.pagination { display: flex; justify-content: flex-end; padding: 18px 20px; }
.two-columns, .three-columns { display: grid; gap: 14px; }
.two-columns { grid-template-columns: 1fr 1fr; }
.three-columns { grid-template-columns: 1.1fr 1fr .9fr; }
.image-field { display: flex; width: 100%; gap: 10px; }
.image-preview { display: grid; width: 130px; height: 100px; margin-top: 12px; place-items: center; overflow: hidden; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; }
.image-preview img { width: 100%; height: 100%; object-fit: contain; }
.drawer-heading { display: flex; align-items: end; justify-content: space-between; width: 100%; padding-right: 16px; }
.drawer-heading p { margin: 0 0 5px; color: #2563eb; font-size: 11px; font-weight: 800; letter-spacing: .12em; }
.drawer-heading h2 { margin: 0; color: #0f172a; }
.sku-alert { margin-bottom: 16px; }
@media (max-width: 900px) {
  .filter-card { grid-template-columns: 1fr 1fr; }
  .filter-card > span { justify-self: start; }
}
</style>
