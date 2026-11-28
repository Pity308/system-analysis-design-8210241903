const api = require('../../utils/api.js')
const fmt = require('../../utils/format.js')
const CONFIG = require('../../config.js')
const app = getApp()

Page({
  data: {
    bound: false,
    patient: null,
    loadingBind: true,

    clinics: [],
    clinicNames: [],
    clinicIndex: 0,
    clinic: null,

    items: [],
    selectedItemIds: [],
    selectedCount: 0,

    days: [],
    dayIndex: 0,

    groups: [],
    loading: false,
    err: '',

    picked: null,
    submitting: false
  },

  onLoad: function () {
    this.setData({ days: fmt.buildDays(CONFIG.DAYS_AHEAD) })
    this.loadReference()
  },

  // 每次回到本页都重新确认绑定状态：从建档页返回时档案才刚生成
  onShow: function () {
    const that = this
    app.ready().then(function () {
      return api.me()
    }).then(function (r) {
      app.setPatient(r && r.patient ? r.patient : null)
      that.setData({
        bound: !!(r && r.bound),
        patient: (r && r.patient) || null,
        loadingBind: false
      })
      // 首次拿到档案且还没查过时段，补一次查询，省掉一次手动点击
      if (r && r.bound && !that.data.groups.length && !that.data.loading) {
        that.query()
      }
    }).catch(function (e) {
      that.setData({ loadingBind: false, err: e.message })
    })
  },

  loadReference: function () {
    const that = this
    Promise.all([api.clinics(), api.treatmentItems()]).then(function (res) {
      const clinics = (res[0] || []).filter(function (c) { return c.status === '营业' })
      const items = (res[1] || []).filter(function (i) { return i.active }).map(function (i) {
        return {
          itemId: i.itemId,
          itemName: i.itemName,
          priceText: fmt.money(i.unitPrice),
          checked: false
        }
      })
      that.setData({
        clinics: clinics,
        clinicNames: clinics.map(function (c) {
          return c.clinicName + '（' + c.city + '）'
        }),
        clinic: clinics[0] || null,
        clinicIndex: 0,
        items: items
      })
      // onShow 可能比本方法先跑完，那时 clinic 还是 null，查询会被跳过；
      // 这里补一次，避免首屏误显示“当天没有可约时段”
      if (that.data.bound && !that.data.groups.length && !that.data.loading) {
        that.query()
      }
    }).catch(function (e) {
      that.setData({ err: e.message })
    })
  },

  goBind: function () {
    wx.navigateTo({ url: '/pages/bind/bind' })
  },

  onClinicChange: function (e) {
    const idx = Number(e.detail.value)
    this.setData({
      clinicIndex: idx,
      clinic: this.data.clinics[idx] || null,
      picked: null
    })
    this.query()
  },

  onDayTap: function (e) {
    const idx = Number(e.currentTarget.dataset.index)
    if (idx === this.data.dayIndex) return
    this.setData({ dayIndex: idx, picked: null })
    this.query()
  },

  toggleItem: function (e) {
    const id = Number(e.currentTarget.dataset.id)
    const items = this.data.items.map(function (i) {
      if (i.itemId !== id) return i
      return Object.assign({}, i, { checked: !i.checked })
    })
    const selected = items.filter(function (i) { return i.checked })
      .map(function (i) { return i.itemId })
    this.setData({
      items: items,
      selectedItemIds: selected,
      selectedCount: selected.length,
      picked: null
    })
    this.query()
  },

  /** 第 3~4 步：查可约时段，按牙医分组展示 */
  query: function () {
    const that = this
    const clinic = this.data.clinic
    if (!clinic) return
    const date = this.data.days[this.data.dayIndex].date
    this.setData({ loading: true, err: '' })

    api.availability(date, clinic.clinicId, this.data.selectedItemIds).then(function (slots) {
      const map = {}
      ;(slots || []).forEach(function (s) {
        if (!map[s.employeeId]) {
          map[s.employeeId] = {
            employeeId: s.employeeId,
            dentistName: s.dentistName,
            title: s.title || '',
            clinicId: s.clinicId,
            clinicName: s.clinicName,
            slots: []
          }
        }
        map[s.employeeId].slots.push({
          start: s.start,
          label: fmt.parseTime(s.start).time,
          endLabel: fmt.parseTime(s.end).time
        })
      })
      const groups = Object.keys(map).map(function (k) { return map[k] })
      groups.forEach(function (g) {
        g.slots = g.slots.map(function (x) {
          const isPicked = that.data.picked &&
            that.data.picked.employeeId === g.employeeId &&
            that.data.picked.start === x.start
          return Object.assign({}, x, { on: !!isPicked })
        })
      })
      that.setData({ groups: groups, loading: false })
    }).catch(function (e) {
      that.setData({ groups: [], loading: false, err: e.message })
    })
  },

  pick: function (e) {
    const ds = e.currentTarget.dataset
    const employeeId = Number(ds.employee)
    const start = ds.start
    const group = this.data.groups.filter(function (g) {
      return g.employeeId === employeeId
    })[0]
    if (!group) return
    const slot = group.slots.filter(function (s) { return s.start === start })[0]
    const p = fmt.parseTime(start)
    this.setData({
      picked: {
        employeeId: employeeId,
        dentistName: group.dentistName,
        clinicId: group.clinicId,
        clinicName: group.clinicName,
        start: start,
        label: slot ? slot.label : p.time,
        dateText: p.date,
        dayText: p.ymd
      }
    })
  },

  unpick: function () {
    this.setData({ picked: null })
  },

  /** 第 5~6 步：提交，成功后给出单号与就诊须知 */
  submit: function () {
    const that = this
    const picked = this.data.picked
    if (!picked || this.data.submitting) return

    this.setData({ submitting: true, err: '' })
    api.book({
      clinicId: picked.clinicId,
      employeeId: picked.employeeId,
      appointmentTime: picked.start,
      expectedMinutes: CONFIG.SLOT_MINUTES,
      treatmentItemIds: this.data.selectedItemIds,
      note: '小程序自助预约'
    }).then(function (res) {
      that.setData({ submitting: false, picked: null })
      const a = (res && res.appointment) || {}
      const warn = (res && res.warnings && res.warnings.length)
        ? '\n\n提示：' + res.warnings.join('；')
        : ''
      wx.showModal({
        title: '预约成功',
        content: (a.clinicName || '') + '\n' + (a.appointmentTime || '').replace('T', ' ').slice(0, 16) +
          '\n' + (a.dentistName || '') + '\n单号：' + (a.appointmentNo || '') +
          '\n\n请提前 10 分钟到店，携带身份证件。' + warn,
        showCancel: false,
        confirmText: '查看我的预约',
        success: function () {
          wx.switchTab({ url: '/pages/mine/mine' })
        }
      })
    }).catch(function (e) {
      that.setData({ submitting: false, err: e.message })
      // 扩展 2a：时段被别人抢走，立刻刷新可约列表
      if (e.status === 409) that.query()
    })
  }
})
