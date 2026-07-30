<script setup>
import AuthForm from '../../components/AuthForm.vue'
</script>

<template>
  <div class="auth-page admin-auth-page">
    <AuthForm
      terminal="seller"
      :allowed-roles="['SELLER', 'ADMIN']"
      title="E-Shop 商家工作台"
      login-subtitle="卖家登录后管理商品、库存、订单和评价"
    />
  </div>
</template>
