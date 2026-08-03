import test from 'node:test'
import assert from 'node:assert/strict'
import { loadExtension } from './test-support.mjs'

test('routing prompt directs exposure questions to fresh local context and events', async () => {
  const fixture = await loadExtension()
  try {
    const prompt = fixture.prompts[0].prompt.content
    assert.match(prompt, /exposure.*get_mindustry_events/is)
    assert.match(prompt, /current.*get_mindustry_context/is)
  } finally { fixture.restore() }
})

test('routing prompt directs history questions to the audit tool', async () => {
  const fixture = await loadExtension()
  try {
    assert.match(fixture.prompts[0].prompt.content, /what did you do.*get_mindustry_action_audit/is)
  } finally { fixture.restore() }
})

test('routing prompt sends emergency stop requests directly to the local stop tool', async () => {
  const fixture = await loadExtension()
  try {
    assert.match(fixture.prompts[0].prompt.content, /stop.*stop_mindustry_actions/is)
  } finally { fixture.restore() }
})

test('construction requests prepare a proposal but never auto-confirm it', async () => {
  const fixture = await loadExtension()
  try {
    const prompt = fixture.prompts[0].prompt.content
    assert.match(prompt, /prepare_mindustry_action/)
    assert.match(prompt, /never auto-confirm/i)
  } finally { fixture.restore() }
})
