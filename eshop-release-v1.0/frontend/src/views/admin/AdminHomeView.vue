<template>
  <section class="admin-dashboard">
    <header>
      <p>WEB ADMIN</p>
      <h1>商城运营控制台</h1>
      <span>集中管理分类、商品、SKU、库存和消费者订单。</span>
    </header>
    <div class="dashboard-grid">
      <RouterLink to="/admin/categories">
        <b>01</b><h2>分类管理</h2><p>维护商城分类结构、排序与启用状态。</p><span>进入管理 →</span>
      </RouterLink>
      <RouterLink to="/admin/products">
        <b>02</b><h2>商品与 SKU</h2><p>创建商品、配置规格价格库存并控制上下架。</p><span>进入管理 →</span>
      </RouterLink>
      <RouterLink to="/admin/orders">
        <b>03</b><h2>订单管理</h2><p>查询订单、查看状态日志、执行发货和取消。</p><span>进入管理 →</span>
      </RouterLink>
      <RouterLink to="/admin/logs">
        <b>04</b><h2>操作日志</h2><p>审计管理端关键业务操作和执行结果。</p><span>查看日志 →</span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.admin-dashboard { max-width: 1180px; margin: 0 auto; }
.admin-dashboard > header { padding: 34px 38px; color: #fff; background: linear-gradient(125deg, #0f172a, #1e3a8a 65%, #2563eb); border-radius: 20px; box-shadow: 0 18px 45px rgba(15, 23, 42, .18); }
.admin-dashboard header > p { margin: 0 0 8px; color: #93c5fd; font-size: 12px; font-weight: 800; letter-spacing: .14em; }
.admin-dashboard h1 { margin: 0 0 10px; font-size: 32px; }
.admin-dashboard header > span { color: #cbd5e1; }
.dashboard-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin-top: 22px; }
.dashboard-grid a { position: relative; min-height: 210px; padding: 28px; overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 17px; box-shadow: 0 10px 32px rgba(15, 23, 42, .05); transition: .2s ease; }
.dashboard-grid a:hover { border-color: #60a5fa; box-shadow: 0 16px 38px rgba(37, 99, 235, .10); transform: translateY(-2px); }
.dashboard-grid b { color: #bfdbfe; font-size: 44px; }
.dashboard-grid h2 { margin: 12px 0 8px; color: #0f172a; }
.dashboard-grid p { min-height: 45px; color: #64748b; line-height: 1.65; }
.dashboard-grid span { color: #2563eb; font-weight: 700; }
@media (max-width: 760px) { .dashboard-grid { grid-template-columns: 1fr; } }
</style>
