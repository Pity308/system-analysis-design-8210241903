<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError, type Appointment, type Clinic } from '../api'
import { session } from '../stores/session'

const clinics = ref<Clinic[]>([])
const clinicId = ref<number>(session.clinicId)
const date = ref(today())
const rows = ref<Appointment[]>([])
const loading = ref(false)
const err = ref('')
const okMsg = ref('')
const sweeping = ref(false)

/** 取消原因：报告 UC-05 约束“取消须填原因”，所以放在行内输入而不是弹窗。 */
const cancelFor = ref<number | null>(null)
const cancelReason = ref('')

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  clinics.value = await api.clinics()
  await load()
})

async function load() {
  loading.value = true
  err.value = ''
  try {
    rows.value = await api.appointments({ date: date.value, clinicId: clinicId.value })
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

const stats = computed(() => {
  const total = rows.value.length
  const attended = rows.value.filter((r) => r.status === '已就诊').length
  const noShow = rows.value.filter((r) => r.status === '未到诊').length
  const cancelled = rows.value.filter((r) => r.status === '已取消').length
  const pending = rows.value.filter((r) => r.status === '待就诊').length
  // 取消率口径：分母 = 已就诊 + 未到诊 + 已取消（待就诊尚未发生，不进分母）
  const denom = attended + noShow + cancelled
  const numer = noShow + cancelled
  const rate = denom ? ((numer / denom) * 100).toFixed(1) : '—'
  return { total, attended, noShow, cancelled, pending, rate, denom, numer }
})

async function act(a: Appointment, action: string) {
  err.value = ''
  okMsg.value = ''
  try {
    const res = await api.changeAppointmentStatus(
      a.appointmentId,
      action,
      action === '已取消' ? cancelReason.value || null : undefined,
    )
    okMsg.value = `${a.appointmentNo} 已改为“${res.appointment.status}”`
    cancelFor.value = null
    cancelReason.value = ''
    await load()
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}

function tagClass(s: string) {
  return s === '已就诊' ? 'green' : s === '未到诊' ? 'red' : s === '已取消' ? 'gray' : 'amber'
}

/**
 * 批量判定逾期未到诊（UC-05）。
 * 与每日 00:05 的定时任务同一套判定：预约结束时间 + 宽限 15 分钟仍为“待就诊”才判未到诊。
 * 前台刷页面时顺手补跑一次，避免当天到诊登记页看到一堆早该判掉的“待就诊”。
 */
async function sweep() {
  if (
    !window.confirm(
      `将把本诊所“预约结束时间 + 15 分钟”已过、且仍为“待就诊”的预约批量判为未到诊。\n判定后不能撤销，只能由患者重新预约。确定继续吗？`,
    )
  )
    return
  err.value = ''
  okMsg.value = ''
  sweeping.value = true
  try {
    const res = await api.sweepNoShow(clinicId.value, 15)
    okMsg.value = res.affected
      ? `已判定 ${res.affected} 条预约未到诊，可在下方列表核对`
      : '没有逾期未就诊的预约，无需判定'
    await load()
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    sweeping.value = false
  }
}
</script>

<template>
  <div class="page-head">
    <h2>到诊登记</h2>
    <span class="sub">UC-05 · 预约状态的唯一改写入口 · 已就诊只进分母，未到诊与已取消进分子</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>
  <div v-if="okMsg" class="notice ok">{{ okMsg }}</div>

  <div class="card">
    <div class="row">
      <div class="field" style="flex: 0 0 260px">
        <label>诊所</label>
        <select v-model.number="clinicId" @change="load">
          <option v-for="c in clinics" :key="c.clinicId" :value="c.clinicId">{{ c.clinicName }}</option>
        </select>
      </div>
      <div class="field" style="flex: 0 0 190px">
        <label>日期</label>
        <input v-model="date" type="date" @change="load" />
      </div>
      <button class="btn" @click="load">刷新</button>
      <button class="btn ghost" :disabled="sweeping" @click="sweep">
        {{ sweeping ? '判定中…' : '批量判定未到诊' }}
      </button>
      <span class="spacer" style="flex: 1"></span>
      <span class="muted">取消率试算</span>
      <b style="font-size: 20px; color: var(--adc-blue-800)">{{ stats.rate }}{{ stats.rate === '—' ? '' : '%' }}</b>
      <span v-if="stats.denom" class="muted" style="font-size: 12px">（{{ stats.numer }}/{{ stats.denom }}）</span>
    </div>

    <div class="divider"></div>

    <div class="grid-3" style="margin-bottom: 4px">
      <div class="kpi"><span class="label">当日预约</span><span class="value">{{ stats.total }}</span></div>
      <div class="kpi">
        <span class="label">待就诊 / 已就诊</span>
        <span class="value">{{ stats.pending }} / {{ stats.attended }}</span>
      </div>
      <div class="kpi">
        <span class="label">未到诊 / 已取消</span>
        <span class="value" style="color: var(--adc-red)">{{ stats.noShow }} / {{ stats.cancelled }}</span>
        <span class="foot">两者都进取消率分子</span>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="card-title">
      预约列表
      <span class="hint">状态只能从“待就诊”出发；已过时点的预约不能再取消。逾期的可用上方“批量判定未到诊”，每日 00:05 也会自动补判</span>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="!rows.length" class="empty">当日该诊所没有预约</div>
    <table v-else class="data">
      <thead>
        <tr>
          <th>预约单号</th>
          <th>时间</th>
          <th>患者</th>
          <th>电话</th>
          <th>牙医</th>
          <th>渠道</th>
          <th>状态</th>
          <th style="width: 260px">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in rows" :key="a.appointmentId">
          <td class="mono">{{ a.appointmentNo }}</td>
          <td class="nowrap">{{ a.appointmentTime.slice(11, 16) }}</td>
          <td>{{ a.patientName }}</td>
          <td class="mono">{{ a.patientPhone }}</td>
          <td>{{ a.dentistName }}</td>
          <td><span class="tag gray">{{ a.channel }}</span></td>
          <td><span class="tag" :class="tagClass(a.status)">{{ a.status }}</span></td>
          <td>
            <template v-if="a.status === '待就诊'">
              <template v-if="cancelFor === a.appointmentId">
                <input
                  v-model="cancelReason"
                  placeholder="取消原因（必填）"
                  style="margin-bottom: 6px"
                />
                <button class="btn sm danger" @click="act(a, '已取消')">确认取消</button>
                <button class="btn sm ghost" @click="cancelFor = null">返回</button>
              </template>
              <template v-else>
                <button class="btn sm" @click="act(a, '已就诊')">已就诊</button>
                <button class="btn sm" @click="act(a, '未到诊')">未到诊</button>
                <button class="btn sm danger" @click="cancelFor = a.appointmentId">取消</button>
              </template>
            </template>
            <span v-else class="muted">已终结</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
