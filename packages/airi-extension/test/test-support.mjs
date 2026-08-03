import assert from 'node:assert/strict'

const extensionUrl = new URL('../mindustry-extension.mjs', import.meta.url)

export async function loadExtension({ responses = {}, offline = false } = {}) {
  const previousFetch = globalThis.fetch
  const previousUrl = process.env.AIRI_MINDUSTRY_CONTEXT_URL
  const requests = []
  process.env.AIRI_MINDUSTRY_CONTEXT_URL = 'http://127.0.0.1:18232/v1/context'

  globalThis.fetch = async url => {
    const parsed = new URL(url)
    requests.push(parsed)
    assert.equal(parsed.hostname, '127.0.0.1', 'extension must never contact a non-loopback host')
    if (offline) throw new TypeError('bridge offline')
    const response = responses[parsed.pathname] ?? { ok: false, status: 404 }
    return {
      ok: response.ok ?? true,
      status: response.status ?? 200,
      async json() { return response.body },
    }
  }

  const tools = new Map()
  const prompts = []
  const extension = (await import(`${extensionUrl.href}?test=${Date.now()}-${Math.random()}`)).default
  await extension.setup({
    kits: {
      async use() {
        return {
          async registerTool(tool) { tools.set(tool.id, tool) },
          async registerToolsetPrompt(prompt) { prompts.push(prompt) },
        }
      },
    },
  })

  return {
    tools,
    prompts,
    requests,
    restore() {
      globalThis.fetch = previousFetch
      if (previousUrl === undefined) delete process.env.AIRI_MINDUSTRY_CONTEXT_URL
      else process.env.AIRI_MINDUSTRY_CONTEXT_URL = previousUrl
    },
  }
}

export async function importProposalStore() {
  try {
    return await import('../proposal-store.mjs')
  }
  catch (error) {
    assert.fail(`Missing proposal confirmation module: ${error.message}`)
  }
}
