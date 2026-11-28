<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError } from '../api'

interface Row {
  employeeId: number
  dentistName: string
  clinicNames: string
  visits: number
  items: number
  revenue: number
  materialCost: number
  operatingCost: number
  netProfit: number
  incentive: number
}

const now = new Date()
const year = ref(now.getFullYear())
const quarter = ref(Math.floor(now.getMonth() / 3) + 1)
const rows = ref<Row[]>([])
const loading = ref(false)
const err = ref('')

const years = computed(() => {
  const y = now.getFullYear()
  return [y, y - 1, y - 2]
})

onMounted(load)

async function load() {
  loading.value = true
  err.value = ''
  try {
    rows.value = await api.quarterReport(year.value, quarter.value)
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** 导出走 CSV：总部要拿去做核账与激励计算，Excel 能直接打开、能再导入别的系统。 */
async function exportCsv() {
  try {
    await api.exportQuarter(year.value, quarter.value)
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}

const totals = computed(() => {
  const r = rows.value.reduce(
    (a, x) => {
      a.revenue += Number(x.revenue)
      a.netProfit += Number(x.netProfit)
      a.incentive += Number(x.incentive)
      a.visits += Number(x.visits)
      return a
    },
    { revenue: 0, netProfit: 0, incentive: 0, visits: 0 },
  )
  return r
})

function money(v: number) {
  return '￥' + Number(v).toFixed(2)
}
</script>

<template>
  <div class="page-head">
    <h2>季度业绩</h2>
    <span class="sub">UC-08 · 只读报表 · 激励基金按净利润 10% 试算</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>

  <div class="card">
    <div class="row">
      <div class="field" style="flex: 0 0 120px">
        <label>年份</label>
        <select v-model.number="year" @change="load">
          <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
        </select>
      </div>
      <div class="field" style="flex: 0 0 120px">
        <label>季度</label>
        <select v-model.number="quarter" @change="load">
          <option :value="1">Q1</option>
          <option :value="2">Q2</option>
          <option :value="3">Q3</option>
          <option :value="4">Q4</option>
        </select>
      </div>
      <button class="btn" @click="load">刷新</button>
      <button class="btn" @click="exportCsv" :disabled="!rows.length">导出 CSV</button>
      <span v-if="loading" class="muted">加载中…</span>
    </div>

    <div class="divider"></div>

    <div class="grid-3">
      <div class="kpi">
        <span class="label">季度收入合计</span>
        <span class="value">{{ money(totals.revenue) }}</span>
        <span class="foot">来自治疗行小计（价目快照）</span>
      </div>
      <div class="kpi">
        <span class="label">净利润合计</span>
        <span class="value">{{ money(totals.netProfit) }}</span>
        <span class="foot">收入 − 材料成本 − 分摊运营费用</span>
      </div>
      <div class="kpi">
        <span class="label">激励基金（10%）</span>
        <span class="value" style="color: var(--adc-green)">{{ money(totals.incentive) }}</span>
        <span class="foot">按净利润 10% 计算</span>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="card-title">
      按牙医汇总
      <span class="hint">牙医不专属某一家诊所，故按人汇总、列出其执业点</span>
    </div>
    <div v-if="!rows.length && !loading" class="empty">该季度没有已完成的就诊记录</div>
    <table v-else class="data">
      <thead>
        <tr>
          <th>牙医</th><th>执业点</th><th>就诊人次</th><th>治疗项次</th>
          <th>收入</th><th>材料成本</th><th>运营费用</th><th>净利润</th><th>激励金额</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.employeeId">
          <td>{{ r.dentistName }}</td>
          <td class="muted">{{ r.clinicNames }}</td>
          <td>{{ r.visits }}</td>
          <td>{{ r.items }}</td>
          <td class="mono">{{ money(r.revenue) }}</td>
          <td class="mono muted">{{ money(r.materialCost) }}</td>
          <td class="mono muted">{{ money(r.operatingCost) }}</td>
          <td class="mono"><b>{{ money(r.netProfit) }}</b></td>
          <td class="mono" style="color: var(--adc-green)">{{ money(r.incentive) }}</td>
        </tr>
      </tbody>
    </table>

    <div class="notice warn" style="margin-top: 12px; font-size: 12.5px">
      口径说明：收入与治疗项次来自本系统治疗行；材料成本与运营费用需财务按季度导入，
      尚未接入前按 0 计，因此“净利润”“激励金额”目前只作试算参考，不能作为实际发放依据。
    </div>
  </div>
</template>
