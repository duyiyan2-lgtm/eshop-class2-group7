<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { updateProfile } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loadingProfile = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const nickname = ref('')
const phone = ref('')

const phoneValid = computed(() => (
  !phone.value.trim() || /^[0-9+\- ]{6,20}$/.test(phone.value.trim())
))

const canSave = computed(() => {
  const normalizedNickname = nickname.value.trim()
  return (
    !loadingProfile.value
    && !saving.value
    && normalizedNickname.length > 0
    && normalizedNickname.length <= 50
    && phoneValid.value
  )
})

const applyUserToForm = (user) => {
  nickname.value = user?.nickname || ''
  phone.value = user?.phone || ''
}

const loadProfile = async () => {
  if (loadingProfile.value) return
  loadingProfile.value = true
  errorMessage.value = ''
  try {
    const user = await auth.refreshCurrentUser()
    applyUserToForm(user)
  } catch (error) {
    errorMessage.value = error.message || '个人资料加载失败'
    applyUserToForm(auth.user)
  } finally {
    loadingProfile.value = false
  }
}

const saveProfile = async () => {
  if (!canSave.value) {
    ElMessage.warning(phoneValid.value ? '请填写有效昵称' : '联系电话格式不正确')
    return
  }
  saving.value = true
  try {
    const user = await updateProfile({
      nickname: nickname.value.trim(),
      phone: phone.value.trim(),
    })
    auth.user = user
    localStorage.setItem('eshop_user', JSON.stringify(user))
    applyUserToForm(user)
    ElMessage.success('个人资料保存成功')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <section class="pc-profile">
    <div class="profile-heading">
      <div>
        <p class="eyebrow">账户设置</p>
        <h2>个人资料</h2>
      </div>
      <el-button :loading="loadingProfile" @click="loadProfile">重新加载</el-button>
    </div>

    <el-alert
      v-if="errorMessage"
      class="error-alert"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
    />

    <el-card v-loading="loadingProfile" shadow="never" class="profile-card">
      <el-form label-position="top" size="large" @submit.prevent="saveProfile">
        <el-form-item label="用户名">
          <el-input :model-value="auth.user?.username" disabled />
          <div class="form-tip">用户名创建后不可修改。</div>
        </el-form-item>

        <el-form-item label="昵称" required>
          <el-input
            v-model="nickname"
            placeholder="请输入昵称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item
          label="联系电话"
          :error="phoneValid ? '' : '请输入 6—20 位数字、空格、+ 或 -'"
        >
          <el-input
            v-model="phone"
            placeholder="请输入手机号或固定电话"
            maxlength="20"
          />
        </el-form-item>

        <el-form-item label="账户角色">
          <el-tag :type="auth.isAdmin ? 'primary' : 'info'">
            {{ auth.isAdmin ? '管理员' : '普通用户' }}
          </el-tag>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="saving"
            :disabled="!canSave"
          >
            保存修改
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </section>
</template>

<style scoped>
.pc-profile {
  max-width: 760px;
}

.profile-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.profile-heading h2 {
  margin: 0;
  color: #172033;
  font-size: 26px;
}

.error-alert {
  margin-bottom: 16px;
}

.profile-card {
  max-width: 620px;
  border-radius: 12px;
}

.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
