export async function api(path, options = {}) {
  const response = await fetch(path, { credentials: 'same-origin', ...options })
  if (response.status === 401) {
    window.location.href = '/login'
    throw new Error('请先登录')
  }
  const type = response.headers.get('content-type') || ''
  const body = type.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) throw new Error(typeof body === 'string' ? body : body.message || '请求失败')
  return body
}

export function post(path, fields) {
  return api(path, { method: 'POST', body: new URLSearchParams(fields) })
}

export async function stream(path, question, onEvent, password = undefined, fileId = undefined) {
  const response = await fetch(path, {
    method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ question, password, fileId })
  })
  if (!response.ok) throw new Error(await response.text())
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let pending = ''
  while (true) {
    const { done, value } = await reader.read()
    pending += decoder.decode(value || new Uint8Array(), { stream: !done })
    pending = pending.replaceAll('\r\n', '\n')
    let cut
    while ((cut = pending.indexOf('\n\n')) >= 0) {
      const block = pending.slice(0, cut).replaceAll('\r', '')
      pending = pending.slice(cut + 2)
      const event = block.match(/^event:\s*(.+)$/m)?.[1]
      const data = block.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
      if (event) onEvent(event, event === 'source' ? JSON.parse(data) : data)
    }
    if (done) break
  }
}
