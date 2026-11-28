<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  api,
  ApiError,
  type Appointment,
  type Patient,
  type TreatmentItem,
  type Visit,
} from '../api'
import { session } from '../stores/session'

const items = ref<TreatmentItem[]>([])
const pendingAppointments = ref<Appointment[]>([])
const visit = ref<Visit | null>(null)
const patient = ref<Patient | null>(null)

const walkinPhone = ref('')
const walkinHits = ref<Patient[]>([])
const walkinSearched = ref(false)

const chiefComplaint = ref('')
const diagnosis = ref('')

// 治疗行录入
const itemId = ref<number | null>(null)
const toothCode = ref('')
const quantity = ref(1)
const consumedUnits = ref(1)
const allergyConfirmed = ref(false)

const err = ref('')
const okMsg = ref('')
const busy = ref(false)

const quadrants = [
  { name: '上颌右侧', codes: ['18', '17', '16', '15', '14', '13', '12', '11'] },
  { name: '上颌左侧', codes: ['21', '22', '23', '24', '25', '26', '27', '28'] },
  { name: '下颌右侧', codes: ['48', '47', '46', '45', '44', '43', '42', '41'] },
  { name: '下颌左侧', codes: ['31', '32', '33', '34', '35', '36', '37', '38'] },
]

const selectedItem = computed(() => items.value.find((i) => i.itemId === itemId.value) || null)

const total = computed(() =>
  (visit.value?.treatments || []).reduce((s, t) => s + Number(t.subtotal), 0),
)

const allergyConflict = computed(() => {
  const contra = selectedItem.value?.contraindication
  if (!contra || !patient.value?.allergies?.length) return ''
  const hit = patient.value.allergies.filter((a) => contra.includes(a.allergenName))
  return hit.length ? hit.map((a) => a.allergenName).join('、') : ''
})

onMounted(async () => {
  items.value = (await api.treatmentItems()).filter((i) => i.active)
  await loadPending()
})

async function loadPending() {
  try {
    pendingAppointments.value = await api.appointments({
      date: today(),
      employeeId: session.dentistId,
      status: '待就诊',
    })
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function startFromAppointment(a: Appointment) {
  err.value = ''
  okMsg.value = ''
  busy.value = true
  try {
    const v = await api.startVisit({ appointmentId: a.appointmentId, chiefComplaint: '' })
    visit.value = v
    patient.value = await api.patient(v.patientId)
    chiefComplaint.value = v.chiefComplaint || ''
    diagnosis.value = v.diagnosis || ''
    await loadPending()
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function searchWalkin() {
  walkinHits.value = await api.patientByPhone(walkinPhone.value.trim())
  walkinSearched.value = true
}

async function startWalkin(p: Patient) {
  err.value = ''
  busy.value = true
  try {
    const v = await api.startVisit({
      patientId: p.patientId,
      employeeId: session.dentistId,
      clinicId: session.clinicId,
    })
    visit.value = v
    patient.value = p
    chiefComplaint.value = ''
    diagnosis.value = ''
    walkinHits.value = []
    walkinSearched.value = false
    walkinPhone.value = ''
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function addTreatment() {
  if (!visit.value) return
  err.value = ''
  okMsg.value = ''
  if (!itemId.value) {
    err.value = '请选择治疗项目'
    return
  }
  busy.value = true
  try {
    const v = await api.addTreatment(visit.value.visitId, {
      itemId: itemId.value,
      toothCode: toothCode.value || null,
      quantity: quantity.value,
      consumedUnits: consumedUnits.value,
      allergyConfirmed: allergyConfirmed.value,
    })
    visit.value = v
    okMsg.value = '治疗行已记录（单价取自价目表，界面不可改价）'
    toothCode.value = ''
    quantity.value = 1
    consumedUnits.value = 1
    allergyConfirmed.value = false
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function confirmVisit() {
  if (!visit.value) return
  err.value = ''
  busy.value = true
  try {
    visit.value = await api.confirmVisit(visit.value.visitId)
    okMsg.value = '就诊已确认，治疗行已冻结；接下来请前台执行 UC-04 生成结算单'
    await loadPending()
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

function pickTooth(code: string) {
  toothCode.value = toothCode.value === code ? '' : code
}
</script>

<template>
  <div class="page-head">
    <h2>到诊与治疗</h2>
    <span class="sub">UC-03 · 当前牙医：{{ session.dentistName }} · 单价来自目录、界面不可编辑</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>
  <div v-if="okMsg" class="notice ok">{{ okMsg }}</div>

  <div class="split">
    <div>
      <!-- 未开就诊时：两种入口 -->
      <template v-if="!visit">
        <div class="card">
          <div class="card-title">今日待就诊<span class="hint">点一条即开就诊（来自预约，患者/诊所自动带出）</span></div>
          <div v-if="!pendingAppointments.length" class="empty">今天没有待就诊的预约</div>
          <table v-else class="data">
            <thead><tr><th>时间</th><th>患者</th><th>电话</th><th>渠道</th></tr></thead>
            <tbody>
              <tr v-for="a in pendingAppointments" :key="a.appointmentId" class="clickable" @click="startFromAppointment(a)">
                <td class="nowrap">{{ a.appointmentTime.slice(11, 16) }}</td>
                <td>{{ a.patientName }}</td>
                <td class="mono">{{ a.patientPhone }}</td>
                <td><span class="tag gray">{{ a.channel }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-title">散客（walk-in）<span class="hint">不补造预约，就诊的预约外键留空</span></div>
          <div class="row">
            <div class="field" style="flex: 2">
              <label>患者电话</label>
              <input v-model="walkinPhone" placeholder="现场登记的手机号" @keyup.enter="searchWalkin" />
            </div>
            <button class="btn" @click="searchWalkin">查找</button>
          </div>
          <div v-if="walkinSearched" style="margin-top: 10px">
            <div v-if="!walkinHits.length" class="notice warn">
              没有档案 —— 请前台先做 UC-01 建档（散客同样需要档案，否则无法记录过敏史）
            </div>
            <table v-else class="data">
              <tbody>
                <tr v-for="p in walkinHits" :key="p.patientId" class="clickable" @click="startWalkin(p)">
                  <td class="mono">{{ p.patientId }}</td>
                  <td>{{ p.fullName }}</td>
                  <td class="muted">{{ p.allergies?.map((a) => a.allergenName).join('、') || '无过敏' }}</td>
                  <td class="right"><span class="tag">开就诊</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>

      <!-- 就诊中 -->
      <template v-else>
        <div class="card">
          <div class="card-title">
            就诊 #{{ visit.visitId }} · {{ patient?.fullName }}
            <span class="tag" :class="visit.status === '已完成' ? 'green' : 'amber'">{{ visit.status }}</span>
            <span v-if="!visit.appointmentId" class="tag gray">散客</span>
          </div>

          <div v-if="patient?.allergies?.length" class="notice warn">
            过敏史：<b>{{ patient.allergies.map((a) => a.allergenName + '（' + a.severity + '）').join('、') }}</b>
            —— 选择含相同材料的项目会被拦下（报告 UC-03 规则 5）
          </div>
          <div v-else class="notice ok" style="font-size: 12.5px">该患者未登记过敏症</div>

          <div class="row" style="margin-bottom: 8px">
            <div class="field" style="flex: 3">
              <label>主诉</label>
              <input v-model="chiefComplaint" :disabled="visit.completed" placeholder="如：右下后牙遇冷酸痛一周" />
            </div>
            <div class="field" style="flex: 2">
              <label>诊断</label>
              <input v-model="diagnosis" :disabled="visit.completed" placeholder="如：46 深龋" />
            </div>
          </div>

          <div class="divider"></div>
          <div class="card-title" style="margin-bottom: 8px">
            治疗行
            <span class="hint">FDI 牙位；单价取目录，不可编辑</span>
          </div>

          <table class="data">
            <thead><tr><th>项目</th><th>牙位</th><th>数量</th><th>单位数</th><th>单价</th><th class="right">小计</th></tr></thead>
            <tbody>
              <tr v-for="t in visit.treatments" :key="t.vtId">
                <td>{{ t.itemName }}</td>
                <td class="mono">{{ t.toothCode || '—' }}</td>
                <td>{{ t.quantity }}</td>
                <td>{{ t.consumedUnits }}</td>
                <td class="mono">￥{{ t.unitPrice }}</td>
                <td class="mono right">￥{{ t.subtotal }}</td>
              </tr>
              <tr v-if="!visit.treatments.length">
                <td colspan="6" class="empty">还没有治疗行，请在下方添加</td>
              </tr>
            </tbody>
            <tfoot v-if="visit.treatments.length">
              <tr>
                <td colspan="5" class="right"><b>试算合计</b></td>
                <td class="mono right"><b>￥{{ total.toFixed(2) }}</b></td>
              </tr>
            </tfoot>
          </table>
        </div>

        <div v-if="!visit.completed" class="card">
          <div class="card-title">添加治疗行<span class="hint">扩展路径 5a：就诊期间可追加项目</span></div>

          <div class="row" style="margin-bottom: 8px">
            <div class="field" style="flex: 3">
              <label>治疗项目</label>
              <select v-model.number="itemId">
                <option :value="null">选择项目</option>
                <option v-for="i in items" :key="i.itemId" :value="i.itemId">
                  {{ i.itemName }} · ￥{{ i.unitPrice }} · 禁忌：{{ i.contraindication || '无' }}
                </option>
              </select>
            </div>
            <div class="field" style="flex: 0 0 110px">
              <label>数量</label>
              <input v-model.number="quantity" type="number" min="1" step="1" />
            </div>
            <div class="field" style="flex: 0 0 110px">
              <label>消耗单位</label>
              <input v-model.number="consumedUnits" type="number" min="0.5" step="0.5" />
            </div>
          </div>

          <div class="field">
            <label>牙位（FDI，整口项目可留空）</label>
            <div style="display: flex; gap: 14px; flex-wrap: wrap; padding-top: 2px">
              <div v-for="q in quadrants" :key="q.name">
                <div class="muted" style="font-size: 10.5px; margin-bottom: 3px">{{ q.name }}</div>
                <div style="display: flex; gap: 3px">
                  <button
                    v-for="c in q.codes"
                    :key="c"
                    class="tooth"
                    :class="{ active: toothCode === c }"
                    @click="pickTooth(c)"
                  >
                    {{ c }}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="allergyConflict" class="notice error" style="margin-top: 8px">
            过敏冲突：该项目含 <b>{{ allergyConflict }}</b>，患者对该材料过敏。请更换材料或治疗方式；
            若已另行确认并继续，请勾选下方确认。
          </div>

          <label class="muted" style="display: flex; gap: 6px; align-items: center; margin: 8px 0">
            <input v-model="allergyConfirmed" type="checkbox" style="width: auto" />
            已与患者另行确认，继续记录（allergyConfirmed）
          </label>

          <button class="btn primary" :disabled="busy" @click="addTreatment">添加到治疗清单</button>
        </div>

        <div v-if="!visit.completed" class="card">
          <div class="card-title">确认就诊<span class="hint">确认后治疗行冻结，更正只能红冲</span></div>
          <div class="notice info" style="font-size: 12.5px">
            预约状态不在这里改 —— 报告要求“已就诊”只能由 UC-05 写，保证唯一改写入口。
          </div>
          <button class="btn primary" :disabled="busy || !visit.treatments.length" @click="confirmVisit">
            确认本次就诊
          </button>
          <button class="btn ghost" style="margin-left: 8px" @click="visit = null; patient = null">放下，返回列表</button>
        </div>
      </template>
    </div>

    <div>
      <div v-if="visit" class="card">
        <div class="card-title">患者</div>
        <div class="muted" style="line-height: 1.9">
          姓名：{{ patient?.fullName }}<br />
          性别：{{ patient?.gender }} · 出生：{{ patient?.birthDate || '—' }}<br />
          电话：{{ patient?.phone }}<br />
          紧急联系人：{{ patient?.emergencyContactName || '未填' }}
          {{ patient?.emergencyContactPhone || '' }}
        </div>
        <div class="divider"></div>
        <div class="muted" style="line-height: 1.9">
          到诊时间：{{ (visit.arrivalTime || '').replace('T', ' ').slice(0, 16) }}<br />
          诊所：{{ visit.clinicName }}<br />
          来源预约：{{ visit.appointmentId ? '#' + visit.appointmentId : '散客（无预约）' }}
        </div>
      </div>

      <div class="card">
        <div class="card-title">本次就诊须知</div>
        <div class="muted" style="line-height: 1.85">
          · 治疗项目必须来自标准目录，不在目录中的需总部审核（扩展 6a）<br />
          · 消耗单位取项目标准单位，不得用“各”等模糊单位<br />
          · 单价默认取标准价目；改价需诊所主管授权，本系统不开放改价入口<br />
          · 治疗行一经确认不得删除，更正以红冲留痕
        </div>
      </div>
    </div>
  </div>
</template>
