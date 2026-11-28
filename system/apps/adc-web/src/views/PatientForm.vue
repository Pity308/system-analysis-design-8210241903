<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, ApiError, type Allergen, type Patient } from '../api'
import { http } from '../api/http'

/** 过敏症逐项登记：报告 6.13 明确“不得只填‘有’”，所以这里只允许从字典里挑。 */
interface AllergyRow {
  allergenId: number | null
  severity: string
  reaction: string
}

const dict = ref<Allergen[]>([])
const searchPhone = ref('')
const found = ref<Patient[]>([])
const current = ref<Patient | null>(null)
const searched = ref(false)

const form = reactive({
  fullName: '',
  gender: '女',
  birthDate: '',
  phone: '',
  email: '',
  address: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  note: '',
})
const allergies = ref<AllergyRow[]>([])
const allowDuplicate = ref(false)

const saving = ref(false)
const err = ref('')
const fieldErr = ref<Record<string, string>>({})
const okMsg = ref('')

onMounted(async () => {
  dict.value = await api.allergens()
})

function addAllergy() {
  allergies.value.push({ allergenId: null, severity: '中度', reaction: '' })
}

function removeAllergy(i: number) {
  allergies.value.splice(i, 1)
}

async function search() {
  err.value = ''
  okMsg.value = ''
  if (!searchPhone.value.trim()) {
    err.value = '请输入电话号码'
    return
  }
  found.value = await api.patientByPhone(searchPhone.value.trim())
  searched.value = true
  current.value = found.value[0] ?? null
}

function pick(p: Patient) {
  current.value = p
  // 命中已有档案时，把电话带进建档表单，避免重复键入
  form.phone = p.phone
}

async function submit() {
  err.value = ''
  fieldErr.value = {}
  okMsg.value = ''
  saving.value = true
  try {
    const body: any = {
      ...form,
      birthDate: form.birthDate || null,
      email: form.email || null,
      address: form.address || null,
      emergencyContactName: form.emergencyContactName || null,
      emergencyContactPhone: form.emergencyContactPhone || null,
      note: form.note || null,
      allowDuplicate: allowDuplicate.value,
      allergies: allergies.value
        .filter((a) => a.allergenId !== null)
        .map((a) => ({
          allergenId: a.allergenId,
          severity: a.severity,
          reaction: a.reaction || null,
        })),
    }
    const saved = await api.createPatient(body)
    okMsg.value = `建档成功：患者编号 ${saved.patientId}，${saved.fullName}。`
    current.value = saved
    found.value = [saved]
    // 清空可变的登记项，保留电话便于连续为同号家庭成员建档
    form.fullName = ''
    form.birthDate = ''
    form.email = ''
    form.address = ''
    form.emergencyContactName = ''
    form.emergencyContactPhone = ''
    form.note = ''
    allergies.value = []
    allowDuplicate.value = false
  } catch (e) {
    if (e instanceof ApiError) {
      err.value = e.message
      if (e.fieldErrors) fieldErr.value = e.fieldErrors
      if (e.code === 'CONFLICT') allowDuplicate.value = false
    } else {
      err.value = String(e)
    }
  } finally {
    saving.value = false
  }
}

function severityTag(s: string) {
  return s === '重度' ? 'red' : s === '中度' ? 'amber' : 'gray'
}
</script>

<template>
  <div class="page-head">
    <h2>患者建档</h2>
    <span class="sub">UC-01 · 主执行者：前台接待员 · 档案全连锁共享，换店不重建</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>
  <div v-if="okMsg" class="notice ok">{{ okMsg }}</div>

  <div class="split">
    <!-- 左：先查后建 -->
    <div>
      <div class="card">
        <div class="card-title">
          调取已有档案
          <span class="hint">按电话查，命中即复用，不新建第二份档案</span>
        </div>
        <div class="row">
          <div class="field" style="flex: 2">
            <label>电话号码</label>
            <input v-model="searchPhone" placeholder="如 13902448101" @keyup.enter="search" />
          </div>
          <button class="btn primary" @click="search">查询</button>
        </div>

        <div v-if="searched" style="margin-top: 12px">
          <div v-if="!found.length" class="empty">
            该号码下没有档案 —— 请在右侧新建，或核对号码后重试。
          </div>
          <table v-else class="data">
            <thead>
              <tr>
                <th>编号</th>
                <th>姓名</th>
                <th>性别</th>
                <th>出生日期</th>
                <th>建档日期</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="p in found"
                :key="p.patientId"
                class="clickable"
                :class="{ selected: current?.patientId === p.patientId }"
                @click="pick(p)"
              >
                <td class="mono">{{ p.patientId }}</td>
                <td>{{ p.fullName }}</td>
                <td>{{ p.gender }}</td>
                <td>{{ p.birthDate || '—' }}</td>
                <td>{{ p.createdDate }}</td>
                <td class="right">
                  <span v-if="p.minor" class="tag amber">未成年</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 选中档案的详情：过敏清单是核心，就诊时必须可见 -->
      <div v-if="current" class="card">
        <div class="card-title">
          {{ current.fullName }} · 过敏与紧急联系人
          <span class="hint">报告 UC-03 规则 5：过敏清单在就诊界面必须可见</span>
        </div>
        <div v-if="!current.allergies?.length" class="notice ok" style="margin-bottom: 10px">
          未登记过敏症（空清单 ≠ 填了一条“有”）
        </div>
        <table v-else class="data">
          <thead>
            <tr><th>过敏源</th><th>类别</th><th>严重程度</th><th>反应描述</th></tr>
          </thead>
          <tbody>
            <tr v-for="a in current.allergies" :key="a.recordId">
              <td>{{ a.allergenName }}</td>
              <td class="muted">{{ a.allergenCategory }}</td>
              <td><span class="tag" :class="severityTag(a.severity)">{{ a.severity }}</span></td>
              <td class="muted">{{ a.reaction || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <div class="divider"></div>
        <div class="muted">
          紧急联系人：{{ current.emergencyContactName || '未填' }}
          {{ current.emergencyContactPhone ? '· ' + current.emergencyContactPhone : '' }}
          <span style="margin-left: 12px">地址：{{ current.address || '未填' }}</span>
        </div>
      </div>
    </div>

    <!-- 右：新建 -->
    <div class="card">
      <div class="card-title">新建档案</div>

      <div class="field">
        <label>姓名<span class="req">*</span></label>
        <input v-model="form.fullName" placeholder="与身份证一致" />
        <span v-if="fieldErr.fullName" class="tag red">{{ fieldErr.fullName }}</span>
      </div>

      <div class="row" style="margin-bottom: 11px">
        <div class="field">
          <label>性别<span class="req">*</span></label>
          <select v-model="form.gender">
            <option>女</option>
            <option>男</option>
          </select>
        </div>
        <div class="field">
          <label>出生日期</label>
          <input v-model="form.birthDate" type="date" />
        </div>
      </div>

      <div class="field">
        <label>联系电话<span class="req">*</span></label>
        <input v-model="form.phone" placeholder="11 位手机号或带区号固话" />
        <span v-if="fieldErr.phone" class="tag red">{{ fieldErr.phone }}</span>
      </div>

      <div class="field">
        <label>电子邮箱</label>
        <input v-model="form.email" placeholder="选填，用于发送就诊提醒" />
        <span v-if="fieldErr.email" class="tag red">{{ fieldErr.email }}</span>
      </div>

      <div class="field">
        <label>通讯地址</label>
        <input v-model="form.address" placeholder="选填" />
      </div>

      <div class="row" style="margin-bottom: 11px">
        <div class="field">
          <label>紧急联系人</label>
          <input v-model="form.emergencyContactName" placeholder="姓名" />
        </div>
        <div class="field">
          <label>紧急联系人电话</label>
          <input v-model="form.emergencyContactPhone" placeholder="电话" />
        </div>
      </div>

      <div class="field">
        <label>备注</label>
        <textarea v-model="form.note" placeholder="选填"></textarea>
      </div>

      <div class="divider"></div>
      <div class="card-title" style="margin-bottom: 8px">
        过敏症
        <span class="hint">逐项登记；无过敏就留空，不要填“有”</span>
        <button class="btn sm" style="margin-left: auto" @click="addAllergy">+ 添加一项</button>
      </div>

      <div v-for="(a, i) in allergies" :key="i" class="row" style="margin-bottom: 8px">
        <div class="field" style="flex: 2">
          <select v-model.number="a.allergenId">
            <option :value="null">选择过敏源</option>
            <option v-for="d in dict" :key="d.allergenId" :value="d.allergenId">
              {{ d.allergenName }}（{{ d.category }}）
            </option>
          </select>
        </div>
        <div class="field" style="flex: 0 0 96px">
          <select v-model="a.severity">
            <option>轻度</option>
            <option>中度</option>
            <option>重度</option>
          </select>
        </div>
        <div class="field" style="flex: 2">
          <input v-model="a.reaction" placeholder="反应描述（选填）" />
        </div>
        <button class="btn sm danger" @click="removeAllergy(i)">删</button>
      </div>
      <div v-if="!allergies.length" class="muted" style="margin-bottom: 8px">暂无登记项</div>

      <label class="muted" style="display: flex; gap: 6px; align-items: center; margin: 10px 0">
        <input v-model="allowDuplicate" type="checkbox" style="width: auto" />
        已核对过同名同电话的档案，确认要新建（allowDuplicate）
      </label>

      <button class="btn primary" :disabled="saving" style="width: 100%" @click="submit">
        {{ saving ? '保存中…' : '保存档案' }}
      </button>
    </div>
  </div>
</template>
