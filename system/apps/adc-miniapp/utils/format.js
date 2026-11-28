/** 时间与文案格式化，页面里只做展示，不参与任何业务判断。 */

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

/** yyyy-MM-dd */
function ymd(d) {
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
}

/** 未来 n 天的日期条：[{date:'2026-11-24', week:'周二', day:'24', month:'11', short:'11/24'}] */
function buildDays(n) {
  const out = []
  const today = new Date()
  for (let i = 0; i < n; i++) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() + i)
    out.push({
      date: ymd(d),
      week: i === 0 ? '今天' : (i === 1 ? '明天' : WEEK[d.getDay()]),
      day: pad(d.getDate()),
      month: pad(d.getMonth() + 1),
      short: pad(d.getMonth() + 1) + '/' + pad(d.getDate())
    })
  }
  return out
}

/**
 * 后端 Jackson 序列化的是带 T 的本地时间：'2026-11-25T09:00:00'。
 * 微信小程序的 JSCore 无法解析 '2026/11/25T09:00:00' 这种串，会返回 Invalid Date。
 * 这里显式提取年月日时分，用本地时间构造 Date，兼容带 T / 空格 / 斜杠三种写法。
 */
function toDate(iso) {
  if (!iso) return null
  const m = String(iso).match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})[T ](\d{1,2}):(\d{2})(?::(\d{2}))?/)
  if (!m) return new Date(iso)
  return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], m[6] ? +m[6] : 0)
}

/** '2026-11-25T09:00:00' -> {date:'11/25 周三', time:'09:00', ymd:'2026-11-25'} */
function parseTime(iso) {
  const d = toDate(iso)
  if (!d || isNaN(d.getTime())) return { date: '', time: '', ymd: '' }
  return {
    date: pad(d.getMonth() + 1) + '/' + pad(d.getDate()) + ' ' + WEEK[d.getDay()],
    time: pad(d.getHours()) + ':' + pad(d.getMinutes()),
    ymd: ymd(d)
  }
}

/** 距现在还有多少小时（用于 24 小时取消闸的界面提示，最终判定仍在服务端）。 */
function hoursUntil(iso) {
  const d = toDate(iso)
  if (!d || isNaN(d.getTime())) return -Infinity
  return (d.getTime() - Date.now()) / 3600000
}

/** '¥280' 形式；价格只读展示，小程序内不参与计算。 */
function money(v) {
  const n = Number(v || 0)
  return '¥' + (n % 1 === 0 ? n.toFixed(0) : n.toFixed(2))
}

module.exports = {
  ymd: ymd,
  buildDays: buildDays,
  parseTime: parseTime,
  hoursUntil: hoursUntil,
  money: money
}
