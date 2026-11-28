import { download, http } from './http'

export { ApiError } from './http'

// ---------------- 类型：与后端领域对象一一对应 ----------------

/** 登录后的员工档案。roleCode 决定能进哪个工作台。 */
export interface StaffProfile {
  employeeId: number
  fullName: string
  username: string
  roleCode: 'RECEPTIONIST' | 'DENTIST' | 'ADMIN'
  roleName: string
  clinicId?: number
  clinicName?: string
}

export interface Clinic {
  clinicId: number
  clinicCode: string
  clinicName: string
  city: string
  address: string
  phone: string
  /** UC-07 规约要求的三个属性，由 V8 迁移补入。 */
  managerName: string | null
  email: string | null
  areaSqm: number | null
  openDate: string
  chairCount: number
  status: string
}

/** UC-07 新建 / 修改诊所的提交体。代码只在新建时生效。 */
export interface ClinicSaveBody {
  clinicCode?: string
  clinicName: string
  city: string
  address?: string | null
  phone?: string | null
  managerName?: string | null
  email?: string | null
  areaSqm?: number | null
  openDate?: string | null
  chairCount?: number | null
  status?: string
}

export interface Allergen {
  allergenId: number
  allergenName: string
  category: string
  description: string
}

export interface PatientAllergy {
  recordId: number
  allergenId: number
  severity: string
  reaction: string
  allergenName: string
  allergenCategory: string
}

export interface Patient {
  patientId: number
  fullName: string
  gender: string
  birthDate: string
  phone: string
  email: string
  address: string
  createdDate: string
  note: string
  emergencyContactName: string
  emergencyContactPhone: string
  allergies: PatientAllergy[]
  minor: boolean
}

export interface TreatmentItem {
  itemId: number
  itemName: string
  standardUnit: number
  unitPrice: number
  contraindication: string
  active: boolean
  description: string
  requiredQualificationId: number
  requiredQualificationName: string
}

export interface Slot {
  employeeId: number
  dentistName: string
  title: string
  clinicId: number
  clinicName: string
  start: string
  end: string
}

export interface Appointment {
  appointmentId: number
  appointmentNo: string
  patientId: number
  employeeId: number
  clinicId: number
  appointmentTime: string
  expectedMinutes: number
  channel: string
  status: string
  createdTime: string
  cancelReason: string
  note: string
  treatmentItemIds: number[]
  patientName: string
  patientPhone: string
  dentistName: string
  clinicName: string
  clinicCode: string
}

/** 逾期未到诊批量判定的结果。 */
export interface SweepResult {
  affected: number
  graceMinutes: number
  sweptAt: string
  items: Appointment[]
}

export interface AppointmentResult {
  appointment: Appointment
  warnings: string[]
}

export interface VisitTreatment {
  vtId: number
  itemId: number
  toothCode: string
  quantity: number
  consumedUnits: number
  unitPrice: number
  subtotal: number
  itemName: string
  standardUnit: number
  contraindication: string
}

export interface Visit {
  visitId: number
  appointmentId: number | null
  patientId: number
  employeeId: number
  clinicId: number
  arrivalTime: string
  endTime: string
  chiefComplaint: string
  diagnosis: string
  status: string
  patientName: string
  patientPhone: string
  dentistName: string
  clinicName: string
  treatments: VisitTreatment[]
  completed: boolean
  /** 已开出的结算单号，未开单为 null（UC-04 入口靠它判断“开单”还是“调单”）。 */
  statementNo: string | null
  statementStatus: string | null
}

export interface StatementLine {
  itemName: string
  toothCode: string
  quantity: number
  unitPrice: number
  subtotal: number
}

export interface Statement {
  statementId: number
  statementNo: string
  visitId: number | null
  patientId: number
  reversalOfId: number | null
  issueDate: string
  treatmentFee: number
  materialFee: number
  discount: number
  totalDue: number
  paidAmount: number
  paymentMethod: string
  paymentTime: string
  status: string
  patientName: string
  clinicName: string
  lines: StatementLine[]
  reversal: boolean
}

export interface Payment {
  paymentId: number
  amount: number
  method: string
  status: string
  paidAt: string
}

export interface Dentist {
  employeeId: number
  fullName: string
  /** 执业证书编号，全连锁唯一（UC-06 字段规则）。 */
  licenseNo: string
  title: string
  specialty: string
  /** 执业点，逗号分隔（一个牙医可在多家诊所轮转）。 */
  clinicNames: string
  /** 持有的资质，逗号分隔，用于展示与到期待提醒。 */
  qualificationNames: string
  /** 最早到期的资质日期，null 表示不限有效期。 */
  earliestExpiry: string | null
}

/** 资质字典项（值表 qualification）。 */
export interface QualificationOption {
  qualificationId: number
  qualificationName: string
  issuingAuthority: string | null
  validUntil: string | null
}

/** 牙医持有的一项资质（关联表 dentist_qualification + 值表）。 */
export interface DentistQualification {
  dqId: number
  qualificationId: number
  qualificationName: string
  issuingAuthority: string | null
  validUntil: string | null
  obtainDate: string | null
  certificateNo: string | null
}

/** 牙医的一个执业点（关联表 dentist_clinic）。 */
export interface DentistClinic {
  dcId: number
  clinicId: number
  clinicCode: string
  clinicName: string
  scheduleNote: string | null
  primary: boolean
}

/**
 * 审计日志（audit_log 的查看视图）。
 * 服务端已经连出操作者姓名与角色，前端不再按 accountId 二次查询。
 */
export interface AuditLogRow {
  logId: number
  accountId: number | null
  actorName: string
  actorRole: string
  action: string
  entity: string
  entityId: number | null
  detail: string
  ip: string
  createdAt: string
}

// ---------------- 接口 ----------------

export const api = {
  login: (username: string, password: string) =>
    http.post<{ token: string; profile: StaffProfile }>('/auth/login', { username, password }),
  me: () => http.get<StaffProfile>('/auth/me'),

  health: () => http.get<any>('/health'),

  clinics: () => http.get<Clinic[]>('/clinics'),
  allergens: () => http.get<Allergen[]>('/allergens'),
  treatmentItems: () => http.get<TreatmentItem[]>('/treatment-items'),
  dentists: () => http.get<Dentist[]>('/dentists'),
  setClinicStatus: (id: number, status: string) =>
    http.post<Clinic>('/clinics/' + id + '/status', { status }),

  // ---- UC-07 维护诊所信息（写接口只给总部） ----
  createClinic: (body: ClinicSaveBody) => http.post<Clinic>('/clinics', body),
  updateClinic: (id: number, body: ClinicSaveBody) => http.put<Clinic>('/clinics/' + id, body),

  // ---- UC-06 维护牙医资质档案 ----
  qualifications: () => http.get<QualificationOption[]>('/qualifications'),
  dentistQualifications: (id: number) =>
    http.get<DentistQualification[]>('/dentists/' + id + '/qualifications'),
  grantQualification: (id: number, body: { qualificationId: number; obtainDate?: string; certificateNo?: string }) =>
    http.post<DentistQualification[]>('/dentists/' + id + '/qualifications', body),
  revokeQualification: (id: number, qualificationId: number) =>
    http.del<DentistQualification[]>('/dentists/' + id + '/qualifications/' + qualificationId),
  dentistClinics: (id: number) => http.get<DentistClinic[]>('/dentists/' + id + '/clinics'),
  addDentistClinic: (id: number, body: { clinicId: number; scheduleNote?: string; primary?: boolean }) =>
    http.post<DentistClinic[]>('/dentists/' + id + '/clinics', body),
  removeDentistClinic: (id: number, clinicId: number) =>
    http.del<DentistClinic[]>('/dentists/' + id + '/clinics/' + clinicId),
  updateDentist: (id: number, body: { licenseNo: string; specialty?: string; title?: string }) =>
    http.put<Dentist>('/dentists/' + id, body),

  patientByPhone: (phone: string) =>
    http.get<Patient[]>('/patients?phone=' + encodeURIComponent(phone)),
  patient: (id: number) => http.get<Patient>('/patients/' + id),
  createPatient: (body: any) => http.post<Patient>('/patients', body),

  availability: (params: {
    date: string
    clinicId?: number
    treatmentItemIds?: number[]
    minutes?: number
  }) => {
    const q = new URLSearchParams({ date: params.date })
    if (params.clinicId) q.set('clinicId', String(params.clinicId))
    if (params.treatmentItemIds?.length)
      params.treatmentItemIds.forEach((id) => q.append('treatmentItemIds', String(id)))
    if (params.minutes) q.set('minutes', String(params.minutes))
    return http.get<Slot[]>('/availability?' + q.toString())
  },

  appointments: (params: { date?: string; clinicId?: number; employeeId?: number; status?: string }) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
    })
    return http.get<Appointment[]>('/appointments?' + q.toString())
  },
  createAppointment: (body: any) => http.post<AppointmentResult>('/appointments', body),
  appointment: (id: number) => http.get<AppointmentResult>('/appointments/' + id),
  appointmentByNo: (no: string) =>
    http.get<AppointmentResult>('/appointments/by-no/' + encodeURIComponent(no)),
  changeAppointmentStatus: (id: number, action: string, cancelReason?: string | null) =>
    http.post<AppointmentResult>('/appointments/' + id + '/status', { action, cancelReason }),

  /**
   * UC-05 后台批处理：把逾期仍“待就诊”的预约批量判为未到诊。
   * 每天 00:05 服务端会自己跑一次，这里是前台的手动补跑。
   */
  sweepNoShow: (clinicId?: number, graceMinutes = 15) => {
    const q = new URLSearchParams({ graceMinutes: String(graceMinutes) })
    if (clinicId) q.set('clinicId', String(clinicId))
    return http.post<SweepResult>('/appointments/sweep?' + q.toString())
  },

  startVisit: (body: any) => http.post<Visit>('/visits', body),
  visit: (id: number) => http.get<Visit>('/visits/' + id),
  addTreatment: (id: number, body: any) => http.post<Visit>('/visits/' + id + '/treatments', body),
  confirmVisit: (id: number) => http.post<Visit>('/visits/' + id + '/confirm'),
  todayVisits: (employeeId: number) =>
    http.get<Visit[]>('/dentists/' + employeeId + '/visits/today'),
  /** 当日就诊列表（前台开单入口），带 statementNo 判断是否已开单。 */
  visitsByDay: (date: string, clinicId?: number) => {
    const q = new URLSearchParams({ date })
    if (clinicId) q.set('clinicId', String(clinicId))
    return http.get<Visit[]>('/visits?' + q.toString())
  },

  generateStatement: (visitId: number) => http.post<Statement>('/statements', { visitId }),
  statement: (id: number) => http.get<Statement>('/statements/' + id),
  statementByNo: (no: string) => http.get<Statement>('/statements/by-no/' + encodeURIComponent(no)),
  patientStatements: (patientId: number) =>
    http.get<Statement[]>('/patients/' + patientId + '/statements'),
  reverseStatement: (id: number, reason: string) =>
    http.post<Statement>('/statements/' + id + '/reverse', { reason }),
  pay: (id: number, amount: string, method: string) =>
    http.post<Statement>('/statements/' + id + '/payments', { amount, method }),
  payments: (id: number) => http.get<Payment[]>('/statements/' + id + '/payments'),

  cancellationReport: (params: { from: string; to: string; clinicId?: number }) => {
    const q = new URLSearchParams({ from: params.from, to: params.to })
    if (params.clinicId) q.set('clinicId', String(params.clinicId))
    return http.get<any>('/reports/cancellation?' + q.toString())
  },
  quarterReport: (year: number, quarter: number) =>
    http.get<any[]>(`/reports/quarter?year=${year}&quarter=${quarter}`),

  /** 导出季度业绩（CSV，Excel 可直接打开）。文件名由前端定，服务端只给内容。 */
  exportQuarter: (year: number, quarter: number) =>
    download(
      `/reports/quarter/export?year=${year}&quarter=${quarter}`,
      `季度业绩-${year}Q${quarter}.csv`,
    ),

  /** 导出经营报表（CSV，含按诊所与按渠道两段）。 */
  exportCancellation: (params: { from: string; to: string; clinicId?: number }) => {
    const q = new URLSearchParams({ from: params.from, to: params.to })
    if (params.clinicId) q.set('clinicId', String(params.clinicId))
    return download(`/reports/cancellation/export?${q.toString()}`,
      `经营报表-${params.from}-${params.to}.csv`)
  },

  /**
   * 审计日志查询，只对总部开放（服务端 @RequireRole(ADMIN) 兜底）。
   * entity 传 appointment / statement / patient … 可缩小到某类记录。
   */
  auditLog: (params: { entity?: string; entityId?: number; limit?: number } = {}) => {
    const q = new URLSearchParams({ limit: String(params.limit ?? 50) })
    if (params.entity) q.set('entity', params.entity)
    if (params.entityId) q.set('entityId', String(params.entityId))
    return http.get<AuditLogRow[]>('/audit?' + q.toString())
  },
}
