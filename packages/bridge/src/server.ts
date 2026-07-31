import { createServer } from 'node:http'

import { createGameContext } from './context.js'
import { getGameSnapshot } from './snapshot.js'

const port = Number.parseInt(process.env.AIRI_MINDUSTRY_BRIDGE_PORT ?? '18232', 10)

if (!Number.isSafeInteger(port) || port < 1 || port > 65535)
  throw new Error('AIRI_MINDUSTRY_BRIDGE_PORT must be an integer between 1 and 65535.')

const server = createServer(async (request, response) => {
  if (request.method !== 'GET' || request.url !== '/v1/context') {
    response.writeHead(404)
    response.end()
    return
  }

  try {
    const body = JSON.stringify(createGameContext(await getGameSnapshot()))
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
