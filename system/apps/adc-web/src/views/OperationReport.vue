<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError, type AuditLogRow, type Clinic } from '../api'

interface Row {
  clinicId: number
  clinicName: string
  total: number
  attended: number
  noShow: number
  cancelled: number
  rate: number
  visits: number
  revenue: number
}

interface ChannelRow {
  channel: string
  total: number
  noShow: number
  cancelled: number
  rate: number
}

const clinics = ref<Clinic[]>([])
const clinicId = ref<number | ''>('')
const from = ref(firstOfMonth(-2))
const to = ref(today())
const rows = ref<Row[]>([])
const channels = ref<ChannelRow[]>([])
const loading = ref(false)
const err = ref('')

/** 审计日志：报告 6.10 只要求“事后查得到”，不占窗体，作为本页底部的只读区。 */
const auditRows = ref<AuditLogRow[]>([])
const auditEntity = ref('')
const auditLoading = ref(false)
const auditErr = ref('')
const AUDIT_LIMIT = 50

const ENTITY_OPTIONS = [
  { value: '', label: '全部记录' },
  { value: 'appointment', label: '预约' },
  { value: 'statement', label: '结算单' },
  { value: 'visit', label: '就诊' },
  { value: 'patient', label: '患者' },
  { value: 'clinic', label: '诊所' },
  { value: 'dentist', label: '牙医' },
  { value: 'dentist_qualification', label: '牙医资质' },
  { value: 'dentist_clinic', label: '执业点' },
  { value: 'user_account', label: '账号' },
]

const ENTITY_LABEL: Record<string, string> = {
  appointment: '预约',
  statement: '结算单',
  visit: '就诊',
  patient: '患者',
  clinic: '诊所',
  dentist: '牙医',
  dentist_qualification: '牙医资质',
  dentist_clinic: '执业点',
  user_account: '账号',
}

const ROLE_LABEL: Record<string, string> = {
  RECEPTIONIST: '前台接待员',
  DENTIST: '牙医',
  ADMIN: '总部管理者',
  SYSTEM: '系统',
}

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function firstOfMonth(offset: number) {
  const d = new Date()
  d.setMonth(d.getMonth() + offset, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

onMounted(async () => {
  clinics.value = await api.clinics()
  await Promise.all([load(), loadAudit()])
})

async function load() {
  loading.value = true
  err.value = ''
  try {    const res = await api.cancellationReport({
      from: from.value,
      to: to.value,
      clinicId: clinicId.value || undefined,
    })
    rows.value = res.byClinic
    channels.value = res.byChannel
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** 导出 CSV：一份文件里含“按诊所”与“按渠道”两段，口径说明也写进表头。 */
async function exportCsv() {
  try {
    await api.exportCancellation({
      from: from.value,
      to: to.value,
      clinicId: clinicId.value || undefined,
    })
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}

function money(v: number) {
  return '￥' + Number(v).toFixed(2)
}

function rateClass(r: number) {
  return r >= 25 ? 'red' : r >= 15 ? 'amber' : 'green'
}

async function loadAudit() {
  auditLoading.value = true
  auditErr.value = ''
  try {
    auditRows.value = await api.auditLog({
      entity: auditEntity.value || undefined,
      limit: AUDIT_LIMIT,
    })
  } catch (e) {
    auditErr.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    auditLoading.value = false
  }
}

/** 2026-11-23T20:35:46 → 11-23 20:35:46，报表页看的是“先后顺序”，年份在筛选条件里。 */
function shortTime(s: string) {
  return s ? s.slice(5, 19).replace('T', ' ') : ''
}

function entityLabel(e: string) {
  return ENTITY_LABEL[e] ?? e
}

function roleLabel(r: string) {
  return ROLE_LABEL[r] ?? r
}
</script>

<template>
  <div class="page-head">
    <h2>经营报表</h2>
    <span class="sub">UC-09 · 只读 · 取消率口径：分子＝未到诊＋已取消，分母＝已就诊＋未到诊＋已取消</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>

  <div class="card">
    <div class="row">
      <div class="field" style="flex: 0 0 150px">
        <label>起始日期</label>
        <input v-model="from" type="date" />
      </div>
      <div class="field" style="flex: 0 0 150px">
        <label>截止日期</label>
        <input v-model="to" type="date" />
      </div>
      <div class="field" style="flex: 0 0 220px">
        <label>诊所</label>
        <select v-model="clinicId">
          <option value="">全部诊所</option>
          <option v-for="c in clinics" :key="c.clinicId" :value="c.clinicId">{{ c.clinicName }}</option>
        </select>
      </div>
      <button class="btn primary" @click="load">生成报表</button>
      <button class="btn" @click="exportCsv" :disabled="!rows.length && !channels.length">导出 CSV</button>
      <span v-if="loading" class="muted">加载中…</span>
    </div>
  </div>

  <div class="card">
    <div class="card-title">
      按诊所对比
      <span class="hint">待就诊的预约尚未发生，不进分母</span>
    </div>
    <div v-if="!rows.length && !loading" class="empty">该时段没有预约记录</div>
    <table v-else class="data">
      <thead>
        <tr>
          <th>诊所</th><th>预约总数</th><th>已就诊</th><th>未到诊</th><th>已取消</th>
          <th>取消率</th><th>就诊人次</th><th>收入</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.clinicId">
          <td>{{ r.clinicName }}</td>
          <td>{{ r.total }}</td>
          <td>{{ r.attended }}</td>
          <td>{{ r.noShow }}</td>
          <td>{{ r.cancelled }}</td>
          <td><span class="tag" :class="rateClass(r.rate)">{{ r.rate.toFixed(1) }}%</span></td>
          <td>{{ r.visits }}</td>
          <td class="mono">{{ money(r.revenue) }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <div class="card">
    <div class="card-title">
      按预约渠道拆分
      <span class="hint">电话 / 到店 / 小程序 —— 用于判断自助预约是否改变了取消率</span>
    </div>
    <div v-if="!channels.length" class="empty">暂无数据</div>
    <table v-else class="data">
      <thead><tr><th>渠道</th><th>预约总数</th><th>未到诊</th><th>已取消</th><th>取消率</th></tr></thead>
      <tbody>
        <tr v-for="c in channels" :key="c.channel">
          <td><span class="tag gray">{{ c.channel }}</span></td>
          <td>{{ c.total }}</td>
          <td>{{ c.noShow }}</td>
          <td>{{ c.cancelled }}</td>
          <td><span class="tag" :class="rateClass(c.rate)">{{ c.rate.toFixed(1) }}%</span></td>
        </tr>
      </tbody>
    </table>
  </div>

  <div class="card">
    <div class="card-title">
      操作日志
      <span class="hint">
        只追加不修改不删除；记录“谁、什么时间、对哪条记录、做了什么”。与业务同一事务写入，回滚则一并回滚
      </span>
    </div>

    <div class="row" style="margin-bottom: 10px">
      <div class="field" style="flex: 0 0 200px">
        <label>记录类型</label>
        <select v-model="auditEntity" @change="loadAudit">
          <option v-for="o in ENTITY_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <button class="btn" :disabled="auditLoading" @click="loadAudit">
        {{ auditLoading ? '加载中…' : '刷新' }}
      </button>
      <span class="spacer" style="flex: 1"></span>
      <span class="muted">最近 {{ auditRows.length }} 条（上限 {{ AUDIT_LIMIT }}）</span>
    </div>

    <div v-if="auditErr" class="notice error">{{ auditErr }}</div>
    <div v-else-if="!auditRows.length && !auditLoading" class="empty">该类型下还没有操作记录</div>
    <table v-else class="data">
      <thead>
        <tr>
          <th style="width: 118px">时间</th>
          <th style="width: 96px">操作者</th>
          <th style="width: 100px">角色</th>
          <th style="width: 116px">动作</th>
          <th style="width: 92px">对象</th>
          <th>详情</th>
          <th style="width: 116px">来源 IP</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="l in auditRows" :key="l.logId">
          <td class="mono nowrap">{{ shortTime(l.createdAt) }}</td>
          <td class="nowrap">{{ l.actorName }}</td>
          <td><span class="tag gray">{{ roleLabel(l.actorRole) }}</span></td>
          <td class="nowrap">{{ l.action }}</td>
          <td class="nowrap">
            {{ entityLabel(l.entity) }}
            <span v-if="l.entityId" class="muted">#{{ l.entityId }}</span>
          </td>
          <td class="muted">{{ l.detail }}</td>
          <td class="mono muted nowrap">{{ l.ip }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
