import { createServer } from 'node:http'

import { createFactoryReport, createGameContext, monitoredEventKinds } from './context.js'
import { getGameSnapshot } from './snapshot.js'
import { suggestBlueprintActions } from './reasoning/suggestion-engine.js'

const port = Number.parseInt(process.env.AIRI_MINDUSTRY_BRIDGE_PORT ?? '18232', 10)
const pluginUrl = process.env.AIRI_MINDUSTRY_PLUGIN_URL ?? 'http://127.0.0.1:18231'

if (!Number.isSafeInteger(port) || port < 1 || port > 65535)
  throw new Error('AIRI_MINDUSTRY_BRIDGE_PORT must be an integer between 1 and 65535.')

let previousSnapshot: Awaited<ReturnType<typeof getGameSnapshot>> | null = null

function isPluginRoute(pathname: string): boolean {
  return pathname === '/v1/audit'
    || pathname === '/v1/action/stop'
    || pathname === '/v1/action/submit'
    || pathname.startsWith('/v1/repair/tasks/')
}

async function readRequestBody(request: import('node:http').IncomingMessage): Promise<string> {
  const chunks: Buffer[] = []
  for await (const chunk of request) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
  return Buffer.concat(chunks).toString('utf8')
}

async function proxyPluginRequest(
  request: import('node:http').IncomingMessage,
  response: import('node:http').ServerResponse,
  url: URL,
): Promise<void> {
  const body = request.method === 'POST' ? await readRequestBody(request) : undefined
  const pluginResponse = await fetch(new URL(`${url.pathname}${url.search}`, pluginUrl), {
    method: request.method,
    headers: body === undefined ? undefined : { 'content-type': 'application/json' },
    body,
  })
  response.writeHead(pluginResponse.status, {
    'content-type': pluginResponse.headers.get('content-type') ?? 'application/json; charset=utf-8',
  })
  response.end(await pluginResponse.text())
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', 'http://127.0.0.1')
  if (isPluginRoute(url.pathname) && (request.method === 'GET' || request.method === 'POST')) {
    try {
      await proxyPluginRequest(request, response, url)
    }
    catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to contact the local Mindustry plugin.'
      response.writeHead(503, { 'content-type': 'application/json; charset=utf-8' })
      response.end(JSON.stringify({ error: message }))
    }
    return
  }
  if (request.method !== 'GET' || !['/v1/context', '/v1/factory-report', '/v1/events', '/v1/suggestions'].includes(url.pathname)) {
    response.writeHead(404)
    response.end()
    return
  }

  try {
    const snapshot = await getGameSnapshot()
    const context = createGameContext(snapshot, previousSnapshot)
    previousSnapshot = snapshot
    const body = url.pathname === '/v1/factory-report'
      ? JSON.stringify({ report: createFactoryReport(snapshot) })
      : url.pathname === '/v1/events'
        ? JSON.stringify({
            events: context.events,
            monitoring: monitoredEventKinds,
          })
        : url.pathname === '/v1/suggestions'
          ? JSON.stringify({
              candidates: suggestBlueprintActions(snapshot),
              policy: 'Candidates require explicit player confirmation and fresh-state validation before placement.',
            })
        : JSON.stringify(context)
    response.writeHead(200, { 'content-type': 'application/json; charset=utf-8' })
    response.end(body)
  }
  catch (error) {
    const message = error instanceof Error ? error.message : 'Unable to read local Mindustry state.'
    response.writeHead(503, { 'content-type': 'application/json; charset=utf-8' })
    response.end(JSON.stringify({ error: message }))
  }
})

server.listen(port, '127.0.0.1', () => {
  console.log(`AIRI Mindustry bridge listening at http://127.0.0.1:${port}/v1/context`)
})
