import { createServer } from 'node:http'

import { createFactoryReport, createGameContext, monitoredEventKinds } from './context.js'
import { getGameSnapshot } from './snapshot.js'
import { suggestBlueprintActions } from './reasoning/suggestion-engine.js'

const port = Number.parseInt(process.env.AIRI_MINDUSTRY_BRIDGE_PORT ?? '18232', 10)

if (!Number.isSafeInteger(port) || port < 1 || port > 65535)
  throw new Error('AIRI_MINDUSTRY_BRIDGE_PORT must be an integer between 1 and 65535.')

let previousSnapshot: Awaited<ReturnType<typeof getGameSnapshot>> | null = null

const server = createServer(async (request, response) => {
  if (request.method !== 'GET' || (request.url !== '/v1/context' && request.url !== '/v1/factory-report' && request.url !== '/v1/events' && request.url !== '/v1/suggestions')) {
    response.writeHead(404)
    response.end()
    return
  }

  try {
    const snapshot = await getGameSnapshot()
    const context = createGameContext(snapshot, previousSnapshot)
    previousSnapshot = snapshot
    const body = request.url === '/v1/factory-report'
      ? JSON.stringify({ report: createFactoryReport(snapshot) })
      : request.url === '/v1/events'
        ? JSON.stringify({
            events: context.events,
            monitoring: monitoredEventKinds,
          })
        : request.url === '/v1/suggestions'
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
