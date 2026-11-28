const api = require('../../utils/api.js')
const fmt = require('../../utils/format.js')
const app = getApp()

const STATUS_CLASS = {
  '待就诊': 'tag-amber',
  '已就诊': 'tag-green',
  '未到诊': 'tag-red',
  '已取消': 'tag-gray'
}

Page({
  data: {
    bound: false,
    loading: true,
    err: '',
    upcoming: [],
    history: [],
    patient: null,
    phoneOf: {}
  },

  onShow: function () {
    this.load()
  },

  onPullDownRefresh: function () {
    this.load().then(function () {
      wx.stopPullDownRefresh()
    })
  },

  load: function () {
    const that = this
    this.setData({ loading: true, err: '' })
    return app.ready().then(function () {
      return Promise.all([api.me(), api.myAppointments(), api.treatmentItems(), api.clinics()])
    }).then(function (res) {
      const me = res[0] || {}
      const list = res[1] || []
      const itemName = {}
      ;(res[2] || []).forEach(function (i) { itemName[i.itemId] = i.itemName })
      const phoneOf = {}
      ;(res[3] || []).forEach(function (c) { phoneOf[c.clinicId] = c.phone })

      const upcoming = []
      const history = []
      list.forEach(function (a) {
        const p = fmt.parseTime(a.appointmentTime)
        const hours = fmt.hoursUntil(a.appointmentTime)
        const pending = a.status === '待就诊'
        const cancellable = pending && hours >= 24
        const row = {
          appointmentId: a.appointmentId,
          appointmentNo: a.appointmentNo,
          dateText: p.date,
          timeText: p.time,
          clinicName: a.clinicName,
          dentistName: a.dentistName,
          clinicPhone: phoneOf[a.clinicId] || '',
          status: a.status,
          statusClass: STATUS_CLASS[a.status] || 'tag-gray',
          itemNames: (a.treatmentItemIds || []).map(function (id) {
            return itemName[id]
          }).filter(Boolean).join('、'),
          note: a.note || '',
          cancelReason: a.cancelReason || '',
          pending: pending,
          cancellable: cancellable,
          // 不足 24 小时时页面只提示，真正的拒绝在服务端（扩展 7a）
          lockHint: pending && !cancellable ? '距开始不足 24 小时，请致电诊所' : ''
        }
        if (pending) upcoming.push(row)
        else history.push(row)
      })

      // 待就诊：后端已按时间正序，最近的一次排最上；历史：倒序，刚过去的在上
      history.reverse()

      that.setData({
        bound: !!(me && me.bound),
        patient: (me && me.patient) || null,
        upcoming: upcoming,
        history: history,
        phoneOf: phoneOf,
        loading: false
      })
    }).catch(function (e) {
      that.setData({ loading: false, err: e.message })
    })
  },

  callClinic: function (e) {
    const phone = e.currentTarget.dataset.phone
    if (!phone) {
      wx.showToast({ title: '请拨打诊所前台电话', icon: 'none' })
      return
    }
    wx.makePhoneCall({ phoneNumber: phone, fail: function () {} })
  },

  /** 扩展 7a：24 小时外才允许自助取消，且必须二次确认 */
  cancel: function (e) {
    const that = this
    const id = Number(e.currentTarget.dataset.id)
    const no = e.currentTarget.dataset.no
    if (!id) return

    wx.showModal({
      title: '取消预约',
      content: '确认取消 ' + no + ' 吗？取消后可重新选择时段。',
      confirmText: '取消预约',
      cancelText: '再想想',
      confirmColor: '#c0392b',
      success: function (r) {
        if (!r.confirm) return
        wx.showLoading({ title: '处理中', mask: true })
        api.cancel(id, '患者在小程序自助取消').then(function () {
          wx.hideLoading()
          wx.showToast({ title: '已取消', icon: 'success' })
          that.load()
        }).catch(function (err) {
          wx.hideLoading()
          wx.showModal({
            title: '没能取消',
            content: err.message,
            showCancel: false
          })
        })
      }
    })
  },

  goBook: function () {
    wx.switchTab({ url: '/pages/book/book' })
  },

  goBind: function () {
    wx.navigateTo({ url: '/pages/bind/bind' })
  }
})
