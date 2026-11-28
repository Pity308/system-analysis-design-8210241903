const api = require('../../utils/api.js')
const app = getApp()

Page({
  data: {
    fullName: '',
    gender: '女',
    birthDate: '',
    phone: '',
    today: '',
    submitting: false,
    err: ''
  },

  onLoad: function () {
    const d = new Date()
    const pad = function (n) { return n < 10 ? '0' + n : '' + n }
    this.setData({
      today: d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
    })
  },

  onInput: function (e) {
    const key = e.currentTarget.dataset.key
    const patch = {}
    patch[key] = e.detail.value
    this.setData(patch)
  },

  onGender: function (e) {
    this.setData({ gender: e.detail.value })
  },

  onBirth: function (e) {
    this.setData({ birthDate: e.detail.value })
  },

  /** 客户端只做“填没填、格式对不对”的粗校验；重名与手机号冲突的判定在服务端 */
  validate: function () {
    const name = this.data.fullName.trim()
    if (name.length < 2) return '请填写真实姓名'
    if (!this.data.birthDate) return '请选择出生日期'
    if (!/^1\d{10}$/.test(this.data.phone.trim())) return '请填写 11 位手机号'
    return ''
  },

  submit: function () {
    const that = this
    if (this.data.submitting) return
    const bad = this.validate()
    if (bad) {
      this.setData({ err: bad })
      return
    }

    this.setData({ submitting: true, err: '' })
    api.bind({
      fullName: this.data.fullName.trim(),
      gender: this.data.gender,
      birthDate: this.data.birthDate,
      phone: this.data.phone.trim()
    }).then(function (patient) {
      app.setPatient(patient)
      that.setData({ submitting: false })
      wx.showToast({ title: '已完成', icon: 'success' })
      setTimeout(function () {
        // 从预约页进来就退回去，让 onShow 重新拉档案；没有上一页时回预约页
        const pages = getCurrentPages()
        if (pages.length > 1) wx.navigateBack()
        else wx.switchTab({ url: '/pages/book/book' })
      }, 700)
    }).catch(function (e) {
      that.setData({ submitting: false, err: e.message })
    })
  }
})
