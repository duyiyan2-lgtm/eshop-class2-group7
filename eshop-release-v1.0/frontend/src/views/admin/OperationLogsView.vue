<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { getOperationLogs } from '../../api/operationLogs'

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

const load = async () => {
  loading.value = true
  try {
    const page = await getOperationLogs({ current: current.value, size: size.value })
    logs.value = page.records
    total.value = page.total
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

const changePage = (page) => {
  current.value = page
  load()
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-title"><div><p class="eyebrow">系统管理</p><h1>后台操作日志</h1></div><el-button @click="load">刷新</el-button></div>
    <el-table :data="logs" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="operatorName" label="操作人" width="130" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="action" label="操作" width="120" />
      <el-table-column prop="requestUri" label="请求地址" min-width="200" />
      <el-table-column prop="success" label="结果" width="100">
        <template #default="scope"><el-tag :type="scope.row.success ? 'success' : 'danger'">{{ scope.row.success ? '成功' : '失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="150" />
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>
    <div class="pagination"><el-pagination background layout="prev, pager, next" :current-page="current" :page-size="size" :total="total" @current-change="changePage" /></div>
  </section>
</template>
