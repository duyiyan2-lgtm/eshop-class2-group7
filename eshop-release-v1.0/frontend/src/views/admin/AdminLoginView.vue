<script setup>
import AuthForm from '../../components/AuthForm.vue'
</script>

<template>
  <div class="auth-page admin-auth-page">
    <AuthForm
      terminal="admin"
      :allowed-roles="['ADMIN']"
      title="E-Shop 平台管理"
      login-subtitle="平台管理员登录后管理买家与卖家账号"
    />
  </div>
</template>
