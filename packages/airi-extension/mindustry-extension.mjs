import { ProposalStore } from './proposal-store.mjs'

const contextUrl = process.env.AIRI_MINDUSTRY_CONTEXT_URL ?? 'http://127.0.0.1:18232/v1/context'
const proposalStore = new ProposalStore()

// NOTICE:
// External extensions are loaded from AIRI user data, while the current SDK
// packages are private workspace dependencies and cannot be resolved there.
// The host resolves kit clients by this stable id (`kit.tool`), so this avoids
// bundling a second SDK copy. Replace this boundary when AIRI publishes an
// external-extension SDK package.
const toolKit = { id: 'kit.tool' }

async function getBridgePayload(path, options = {}) {
  const response = await fetch(new URL(path, contextUrl), options)
  const payload = await response.json()
  if (!response.ok) {
    const reason = payload && typeof payload === 'object' && typeof payload.reasonCode === 'string'
      ? payload.reasonCode
      : `HTTP ${response.status}`
    throw new Error(`The local Mindustry bridge rejected the request: ${reason}.`)
  }
  return payload
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

async function getRepairTaskStatus({ taskId }) {
  if (typeof taskId !== 'string' || taskId.length === 0) throw new TypeError('taskId is required.')
  return getBridgePayload(`/v1/repair/tasks/${encodeURIComponent(taskId)}`)
}

async function getActionAudit({ before, limit } = {}) {
  const parameters = new URLSearchParams()
  if (Number.isSafeInteger(before)) parameters.set('before', String(before))
  if (Number.isSafeInteger(limit)) parameters.set('limit', String(limit))
  return getBridgePayload(`/v1/audit${parameters.size ? `?${parameters}` : ''}`)
}

function stopMindustryActions() {
  return getBridgePayload('/v1/action/stop', { method: 'POST' })
}

async function currentGameTick() {
  const context = await getBridgePayload('/v1/context')
  if (!context?.snapshot || !Number.isSafeInteger(context.snapshot.gameTick))
    throw new TypeError('The local Mindustry bridge returned an invalid context payload.')
  return context.snapshot.gameTick
}

async function prepareMindustryAction({ action }) {
  if (!action || typeof action !== 'object') throw new TypeError('A structured action is required.')
  if (action.requiresConfirmation !== true) throw new Error('Only actions requiring player confirmation may be prepared.')
  if (!Number.isSafeInteger(action.refTick)) throw new TypeError('Action refTick must be a valid game tick.')
  return proposalStore.prepare(action, await currentGameTick())
}

async function confirmMindustryAction({ proposalId }) {
  if (typeof proposalId !== 'string' || proposalId.length === 0) throw new TypeError('proposalId is required.')
  const action = proposalStore.confirm(proposalId, await currentGameTick())
  return getBridgePayload('/v1/action/submit', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(action),
  })
}

function cancelMindustryAction({ proposalId }) {
  if (typeof proposalId !== 'string' || proposalId.length === 0) throw new TypeError('proposalId is required.')
  proposalStore.cancel(proposalId)
  return { cancelled: true, proposalId }
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
- "What did you do?", repair progress, task state, history, or audit: get_mindustry_action_audit; use get_mindustry_repair_status too when a task ID is known.
- A player-requested emergency stop: stop_mindustry_actions.
- Construction or repair actions: prepare_mindustry_action first. Explain the reviewed proposal and wait for an explicit player request that names its proposal ID before confirm_mindustry_action. Never auto-confirm an action, infer consent, or reuse an old confirmation.

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
    await tools.registerTool({
      id: 'get_mindustry_repair_status', title: 'Get Mindustry repair task status',
      description: 'Reads the authoritative lifecycle state of a specific local repair task.',
      activation: { keywords: ['mindustry', 'repair', 'task', 'poly', 'status'] },
      inputSchema: { type: 'object', properties: { taskId: { type: 'string' } }, required: ['taskId'], additionalProperties: false },
      isAvailable: async () => { try { await getContextSummary(); return true } catch { return false } }, execute: getRepairTaskStatus,
    })
    await tools.registerTool({
      id: 'get_mindustry_action_audit', title: 'Get Mindustry action audit',
      description: 'Reads player-readable, newest-first local action history.',
      activation: { keywords: ['mindustry', 'history', 'audit', 'what did you do', 'actions'] },
      inputSchema: { type: 'object', properties: { before: { type: 'integer' }, limit: { type: 'integer' } }, required: [], additionalProperties: false },
      isAvailable: async () => { try { await getContextSummary(); return true } catch { return false } }, execute: getActionAudit,
    })
    await tools.registerTool({
      id: 'stop_mindustry_actions', title: 'Stop Mindustry companion actions',
      description: 'Stops active companion tasks only after the player explicitly requests it.',
      activation: { keywords: ['mindustry', 'stop', 'cancel', 'emergency'] },
      inputSchema: { type: 'object', properties: {}, required: [], additionalProperties: false },
      isAvailable: async () => { try { await getContextSummary(); return true } catch { return false } }, execute: stopMindustryActions,
    })
    await tools.registerTool({
      id: 'prepare_mindustry_action', title: 'Prepare Mindustry action',
      description: 'Creates an immutable, player-reviewable action proposal without changing the game.',
      activation: { keywords: ['mindustry', 'prepare', 'blueprint', 'repair', 'build'] },
      inputSchema: { type: 'object', properties: { action: { type: 'object' } }, required: ['action'], additionalProperties: false },
      isAvailable: async () => { try { await getContextSummary(); return true } catch { return false } }, execute: prepareMindustryAction,
    })
    await tools.registerTool({
      id: 'confirm_mindustry_action', title: 'Confirm prepared Mindustry action',
      description: 'Submits exactly one fresh, reviewed proposal after an explicit player confirmation.',
      activation: { keywords: ['mindustry', 'confirm', 'proposal'] },
      inputSchema: { type: 'object', properties: { proposalId: { type: 'string' } }, required: ['proposalId'], additionalProperties: false },
      isAvailable: async () => { try { await getContextSummary(); return true } catch { return false } }, execute: confirmMindustryAction,
    })
    await tools.registerTool({
      id: 'cancel_mindustry_action', title: 'Cancel prepared Mindustry action',
      description: 'Discards a pending proposal without sending it to Mindustry.',
      activation: { keywords: ['mindustry', 'cancel', 'proposal'] },
      inputSchema: { type: 'object', properties: { proposalId: { type: 'string' } }, required: ['proposalId'], additionalProperties: false },
      isAvailable: async () => true, execute: cancelMindustryAction,
    })
  },
}
