/**
 * WebSocket 客户端封装: 连接 /ws/vote?token=xxx, 处理重连与主题更新消息
 */
class WsClient {
  constructor() {
    this.socket = null
    this.connected = false
    this.listeners = new Set()
    this.reconnectTimer = null
    this.manualClose = false
  }

  connect() {
    const token = localStorage.getItem('voting_token')
    if (!token) return
    this.manualClose = false
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${location.host}/ws/vote?token=${encodeURIComponent(token)}`
    try {
      this.socket = new WebSocket(url)
    } catch (e) {
      return
    }
    this.socket.onopen = () => {
      this.connected = true
      this.emit({ type: 'connected' })
    }
    this.socket.onmessage = evt => {
      try {
        const msg = JSON.parse(evt.data)
        this.emit(msg)
      } catch (e) {
        /* ignore */
      }
    }
    this.socket.onclose = () => {
      this.connected = false
      this.emit({ type: 'disconnected' })
      if (!this.manualClose) {
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = setTimeout(() => this.connect(), 3000)
      }
    }
    this.socket.onerror = () => {
      /* onclose will handle */
    }
  }

  /** 订阅消息 */
  subscribe(fn) {
    this.listeners.add(fn)
    return () => this.listeners.delete(fn)
  }

  emit(msg) {
    this.listeners.forEach(fn => {
      try {
        fn(msg)
      } catch (e) { /* ignore */ }
    })
  }

  close() {
    this.manualClose = true
    clearTimeout(this.reconnectTimer)
    if (this.socket) this.socket.close()
  }
}

export const wsClient = new WsClient()

/** 建立连接(登录成功后调用) */
export function connectWs() {
  wsClient.connect()
}

/** 断开连接(登出时调用) */
export function disconnectWs() {
  wsClient.close()
}
