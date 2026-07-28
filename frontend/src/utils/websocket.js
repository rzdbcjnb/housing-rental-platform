export function websocketUrl(path, token) {
  const configured = (import.meta.env.VITE_WS_BASE_URL || '').replace(/\/$/, '')
  const base = configured || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`
  const separator = path.includes('?') ? '&' : '?'
  return `${base}${path}${separator}token=${encodeURIComponent(token)}`
}
