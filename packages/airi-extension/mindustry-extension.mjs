const contextUrl = process.env.AIRI_MINDUSTRY_CONTEXT_URL ?? 'http://127.0.0.1:18232/v1/context'

// NOTICE:
// External extensions are loaded from AIRI user data, while the current SDK
// packages are private workspace dependencies and cannot be resolved there.
// The host resolves kit clients by this stable id (`kit.tool`), so this avoids
// bundling a second SDK copy. Replace this boundary when AIRI publishes an
// external-extension SDK package.
const toolKit = { id: 'kit.tool' }

async function getBridgePayload(path) {
  const response = await fetch(new URL(path, contextUrl))
  if (!response.ok)
    throw new Error(`The local Mindustry bridge responded with HTTP ${response.status}.`)

  return response.json()
}

async function getContextSummary() {
  const context = await getBridgePayload('/v1/context')
  if (!context || typeof context !== 'object' || typeof context.summary !== 'string')
    throw new TypeError('The local Mindustry bridge returned an invalid context payload.')

  // Keep tool output compact. Returning the nested debug snapshot made model
  // responses less reliable even though the local bridge data was correct.
  return { summary: context.summary }
}

async function getFactoryReport() {
  const payload = await getBridgePayload('/v1/factory-report')
  if (!payload || typeof payload !== 'object' || typeof payload.report !== 'string')
    throw new TypeError('The local Mindustry bridge returned an invalid factory report.')

  return { report: payload.report }
}

async function getGameEvents() {
  const payload = await getBridgePayload('/v1/events')
  if (
    !payload
    || typeof payload !== 'object'
    || !Array.isArray(payload.events)
    || !Array.isArray(payload.monitoring)
    || !payload.monitoring.every(item => typeof item === 'string')
  )
    throw new TypeError('The local Mindustry bridge returned invalid game events.')

  return { events: payload.events, monitoring: payload.monitoring }
}

async function getBlueprintSuggestions() {
  const payload = await getBridgePayload('/v1/suggestions')
  if (!payload || typeof payload !== 'object' || !Array.isArray(payload.candidates) || typeof payload.policy !== 'string')
    throw new TypeError('The local Mindustry bridge returned invalid blueprint suggestions.')

  return payload
}

/**
 * Registers a read-only Mindustry-context tool for AIRI conversations.
 *
 * This extension never connects to public servers or issues game commands. Its
 * only external request targets the user-configured, loopback bridge endpoint.
 */
export default {
  id: 'airi-mindustry',
  async setup({ kits }) {
    const tools = await kits.use(toolKit)
    // Tool availability alone leaves live-game reads up to model discretion.
    // This shared prompt gives the model a small, explicit intent router while
    // keeping static Mindustry knowledge and unrelated chat free of requests.
    await tools.registerToolsetPrompt({
      id: 'mindustry-live-state-routing',
      prompt: {
        id: 'mindustry-live-state-routing',
        title: 'Mindustry live-state routing',
        content: `For any question whose answer depends on the player's current Mindustry match, perform a fresh local tool read before answering. Do not say that you lack current state without calling the relevant available tool.

Route live intents as follows:
- Base status, resources, core health, enemies, wave, or "what should I do now?": get_mindustry_context.
- Attacks, exposure, damage, alerts, shortages, or immediate danger: get_mindustry_events.
- Power, production, factories, or bottlenecks: get_mindustry_factory_report.
- Defenses, turrets, walls, blueprints, or placement recommendations: get_mindustry_blueprint_suggestions.

Static rules, block facts, recipes, and unrelated conversation do not need a live Mindustry tool. Tool outputs describe the current local game only; do not invent missing facts.`,
      },
    })
    await tools.registerTool({
      id: 'get_mindustry_context',
      title: 'Get Mindustry context',
      description: 'Reads a concise, current summary of the user\'s local Mindustry match. Use only when Mindustry context would help answer the user.',
      activation: {
        keywords: ['mindustry', 'wave', 'core', 'factory', 'resources'],
      },
      inputSchema: {
        type: 'object',
        properties: {},
        required: [],
        additionalProperties: false,
      },
      isAvailable: async () => {
        try {
          await getContextSummary()
          return true
        }
        catch {
          return false
        }
      },
      execute: getContextSummary,
    })
    await tools.registerTool({
      id: 'get_mindustry_factory_report',
      title: 'Get Mindustry factory report',
      description: 'Reads the current local Mindustry power balance and production-building footprint. Use for factory bottlenecks, power, or production questions.',
      activation: {
        keywords: ['mindustry', 'factory', 'power', 'production', 'bottleneck', 'resources'],
      },
      inputSchema: {
        type: 'object',
        properties: {},
        required: [],
        additionalProperties: false,
      },
      isAvailable: async () => {
        try {
          await getFactoryReport()
          return true
        }
        catch {
          return false
        }
      },
      execute: getFactoryReport,
    })
    await tools.registerTool({
      id: 'get_mindustry_events',
      title: 'Get Mindustry events',
      description: 'Reads newly detected local Mindustry alerts, such as new waves, enemy pressure, core damage, power shortages, and low resources.',
      activation: {
        keywords: ['mindustry', 'alert', 'events', 'attack', 'damage', 'shortage', 'wave'],
      },
      inputSchema: {
        type: 'object',
        properties: {},
        required: [],
        additionalProperties: false,
      },
      isAvailable: async () => {
        try {
          await getGameEvents()
          return true
        }
        catch {
          return false
        }
      },
      execute: getGameEvents,
    })
    await tools.registerTool({
      id: 'get_mindustry_blueprint_suggestions',
      title: 'Get Mindustry blueprint suggestions',
      description: 'Returns deterministic, locally validated blueprint candidates for active defense exposure. Candidates are not actions and still require player confirmation plus mod-side preflight.',
      activation: {
        keywords: ['mindustry', 'defense', 'blueprint', 'turret', 'wall', 'exposed'],
      },
      inputSchema: {
        type: 'object',
        properties: {},
        required: [],
        additionalProperties: false,
      },
      isAvailable: async () => {
        try {
          await getBlueprintSuggestions()
          return true
        }
        catch {
          return false
        }
      },
      execute: getBlueprintSuggestions,
    })
  },
}
