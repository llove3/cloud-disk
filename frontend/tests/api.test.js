import test from 'node:test'
import assert from 'node:assert/strict'
import { stream } from '../src/api.js'

test('SSE parser handles split CRLF frames and error events', async () => {
  const previousFetch = globalThis.fetch
  const chunks = [
    'event: source\r\ndata: {"number":1,"fileId":3}\r\n\r',
    '\nevent:token\r\ndata:回答\r\n\r\nevent:error\r\ndata:模型暂时不可用\r\n\r\n'
  ]
  globalThis.fetch = async () => new Response(new ReadableStream({
    start(controller) { for (const chunk of chunks) controller.enqueue(new TextEncoder().encode(chunk)); controller.close() }
  }), { headers: { 'Content-Type': 'text/event-stream' } })
  const events = []
  try {
    await stream('/api/ai/chat', '问题', (name, data) => events.push([name, data]))
    assert.deepEqual(events, [
      ['source', { number: 1, fileId: 3 }],
      ['token', '回答'],
      ['error', '模型暂时不可用']
    ])
  } finally { globalThis.fetch = previousFetch }
})

test('stream sends selected file IDs and exposes request errors', async () => {
  const previousFetch = globalThis.fetch
  let request
  globalThis.fetch = async (_path, options) => {
    request = JSON.parse(options.body)
    return new Response(JSON.stringify({ message: '文件不可访问' }), {
      status: 400, headers: { 'Content-Type': 'application/json' }
    })
  }
  try {
    await assert.rejects(stream('/api/ai/chat', '讲了啥', () => {}, [1, 3]), /文件不可访问/)
    assert.deepEqual(request.fileIds, [1, 3])
    assert.equal(request.password, undefined)
  } finally { globalThis.fetch = previousFetch }
})
