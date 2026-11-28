<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  api,
  ApiError,
  type Clinic,
  type Payment,
  type Statement,
  type Visit,
} from '../api'
import { session } from '../stores/session'

const clinics = ref<Clinic[]>([])
const clinicId = ref<number>(session.clinicId)
const date = ref(today())
const visits = ref<Visit[]>([])
const current = ref<Statement | null>(null)
const payments = ref<Payment[]>([])
const loading = ref(false)
const err = ref('')
const okMsg = ref('')

const payAmount = ref('')
const payMethod = ref('现金')
const reverseReason = ref('')
const showReverse = ref(false)

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
    visits.value = await api.visitsByDay(date.value, clinicId.value)
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

const remaining = computed(() => {
  if (!current.value) return 0
  return Number((current.value.totalDue - current.value.paidAmount).toFixed(2))
})

async function openVisit(v: Visit) {
  err.value = ''
  okMsg.value = ''
  if (v.status !== '已完成') {
    err.value = '该次就诊尚未确认完成，不能开结算单（UC-03 基本路径第 8 步）'
    return
  }
  try {
    let st: Statement
    if (v.statementNo) {
      st = await api.statementByNo(v.statementNo)
    } else {
      st = await api.generateStatement(v.visitId)
      okMsg.value = `已生成结算单 ${st.statementNo}，应付 ￥${st.totalDue}`
      await load()
    }
    current.value = st
    payAmount.value = String(Math.max(0, remaining.value))
    payments.value = await api.payments(st.statementId)
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}

async function pay() {
  if (!current.value) return
  err.value = ''
  okMsg.value = ''
  try {
    const st = await api.pay(current.value.statementId, payAmount.value, payMethod.value)
    current.value = st
    payments.value = await api.payments(st.statementId)
    payAmount.value = String(Math.max(0, remaining.value))
    okMsg.value =
      st.status === '已结算'
        ? `收款完成，结算单已结清（￥${st.paidAmount}）`
        : `已收 ￥${st.paidAmount}，尚欠 ￥${remaining.value}，状态停留在“待支付”`
    await load()
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}

async function reverse() {
  if (!current.value) return
  err.value = ''
  okMsg.value = ''
  try {
    const red = await api.reverseStatement(current.value.statementId, reverseReason.value)
    okMsg.value = `已开具红字结算单 ${red.statementNo}（${red.totalDue} 元），原单置为“已红冲”`
    current.value = red
    payments.value = []
    showReverse.value = false
    reverseReason.value = ''
    await load()
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}

function statusTag(s: string) {
  return s === '已结算' ? 'green' : s === '已红冲' ? 'red' : s === '待支付' ? 'amber' : 'gray'
}
</script>

<template>
  <div class="page-head">
    <h2>结算与收款</h2>
    <span class="sub">UC-04 · 金额只读、逐行明细来自治疗行快照 · 本期收款在收银台完成，不接在线支付</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>
  <div v-if="okMsg" class="notice ok">{{ okMsg }}</div>

  <div class="split">
    <div>
      <div class="card">
        <div class="card-title">当日就诊<span class="hint">点一行即开单（已开过的直接调出原单）</span></div>
        <div class="row" style="margin-bottom: 10px">
          <div class="field" style="flex: 0 0 240px">
            <label>诊所</label>
            <select v-model.number="clinicId" @change="load">
              <option v-for="c in clinics" :key="c.clinicId" :value="c.clinicId">{{ c.clinicName }}</option>
            </select>
          </div>
          <div class="field" style="flex: 0 0 180px">
            <label>日期</label>
            <input v-model="date" type="date" @change="load" />
          </div>
          <button class="btn" @click="load">刷新</button>
        </div>

        <div v-if="loading" class="loading">加载中…</div>
        <div v-else-if="!visits.length" class="empty">当日没有就诊记录</div>
        <table v-else class="data">
          <thead>
            <tr><th>就诊</th><th>患者</th><th>牙医</th><th>治疗行</th><th>状态</th><th>结算单</th></tr>
          </thead>
          <tbody>
            <tr v-for="v in visits" :key="v.visitId" class="clickable" @click="openVisit(v)">
              <td class="mono">#{{ v.visitId }}</td>
              <td>{{ v.patientName }}</td>
              <td>{{ v.dentistName }}</td>
              <td>{{ v.treatments?.length || 0 }} 项</td>
              <td>
                <span class="tag" :class="v.status === '已完成' ? 'green' : 'amber'">{{ v.status }}</span>
              </td>
              <td>
                <span v-if="v.statementNo" class="mono">{{ v.statementNo }}</span>
                <span v-else class="muted">未开单</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="current" class="card">
        <div class="card-title">
          结算单 {{ current.statementNo }}
          <span class="tag" :class="statusTag(current.status)" style="margin-left: 6px">{{ current.status }}</span>
          <span v-if="current.reversal" class="tag red">红字单 · 指向原单 #{{ current.reversalOfId }}</span>
        </div>

        <table class="data">
          <thead>
            <tr><th>治疗名称</th><th>牙位</th><th>数量</th><th>单价</th><th class="right">小计</th></tr>
          </thead>
          <tbody>
            <tr v-for="(l, i) in current.lines" :key="i">
              <td>{{ l.itemName }}</td>
              <td class="mono">{{ l.toothCode || '—' }}</td>
              <td>{{ l.quantity }}</td>
              <td class="mono">￥{{ l.unitPrice }}</td>
              <td class="mono right">￥{{ l.subtotal }}</td>
            </tr>
            <tr v-if="!current.lines?.length">
              <td colspan="5" class="muted">红字单不重复列明细，金额取原单的相反数</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="4" class="right"><b>应付总额</b></td>
              <td class="mono right"><b>￥{{ current.totalDue }}</b></td>
            </tr>
            <tr>
              <td colspan="4" class="right">已收 / 未收</td>
              <td class="mono right">￥{{ current.paidAmount }} / ￥{{ remaining }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>

    <div>
      <div v-if="current" class="card">
        <div class="card-title">收款<span class="hint">一张单可分多笔，收足自动转“已结算”</span></div>

        <div class="row" style="margin-bottom: 8px">
          <div class="field" style="flex: 2">
            <label>本次收款金额</label>
            <input v-model="payAmount" :disabled="current.status === '已结算' || current.reversal" />
          </div>
          <div class="field" style="flex: 1">
            <label>方式</label>
            <select v-model="payMethod">
              <option>现金</option>
              <option>刷卡</option>
              <option>转账</option>
            </select>
          </div>
        </div>
        <button
          class="btn primary"
          style="width: 100%"
          :disabled="current.status === '已结算' || current.reversal || remaining <= 0"
          @click="pay"
        >
          {{ current.status === '已结算' ? '已结清' : current.reversal ? '红字单不可收款' : '确认收款' }}
        </button>

        <div class="divider"></div>
        <div class="card-title" style="margin-bottom: 6px">收款流水</div>
        <div v-if="!payments.length" class="muted">暂无流水</div>
        <table v-else class="data">
          <thead><tr><th>时间</th><th>金额</th><th>方式</th></tr></thead>
          <tbody>
            <tr v-for="p in payments" :key="p.paymentId">
              <td class="muted">{{ (p.paidAt || '').replace('T', ' ').slice(0, 16) }}</td>
              <td class="mono">￥{{ p.amount }}</td>
              <td>{{ p.method }}</td>
            </tr>
          </tbody>
        </table>

        <div class="divider"></div>
        <template v-if="!current.reversal">
          <button v-if="!showReverse" class="btn danger" style="width: 100%" @click="showReverse = true">
            开具红字结算单
          </button>
          <template v-else>
            <div class="field">
              <label>红冲原因（留痕）</label>
              <input v-model="reverseReason" placeholder="如：材料过敏改方案、收费口径有误" />
            </div>
            <button class="btn danger" style="width: 100%" @click="reverse">确认红冲</button>
            <button class="btn ghost" style="width: 100%; margin-top: 6px" @click="showReverse = false">取消</button>
          </template>
          <div class="muted" style="margin-top: 8px; line-height: 1.6">
            红冲不删除原单：另开一张负额红字单并指回原单，两单在报表上自动抵销。
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
