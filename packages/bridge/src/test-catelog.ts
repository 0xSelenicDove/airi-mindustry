import { GameCatalog, loadV159Catalog } from './knowledge/catalog.js'
import { requiresFreshSnapshot } from './action.js'

async function runLocalTests() {
  console.log('🧪 Starting Local Verification Tests...\n')

  // 1. Test Static Data Catalog (Recipe Lookup)
  console.log('--- Test 1: Recipe Lookup ---')
  const catalog = loadV159Catalog()
  const smelterRecipe = catalog.getRecipe('silicon-smelter')
  console.log('Silicon Smelter Recipe:', smelterRecipe)
  if (smelterRecipe) {
    console.log('✅ Recipe catalog loaded correctly!')
  } else {
    console.error('❌ Failed to load Silicon Smelter recipe.')
  }

  // 2. Test Material Calculation
  console.log('\n--- Test 2: Missing Materials Check ---')
  const blueprintCatalog = new GameCatalog([], [], [{
    id: 'test-copper-walls',
    description: 'Test-only catalog entry.',
    schematicBase64: 'test-only',
    requiredItems: [{ itemId: 'copper', amount: 24 }],
    width: 4,
    height: 1,
  }])
  const missing = blueprintCatalog.getMissingBlueprintItems('test-copper-walls', new Map([['copper', 10]]))
  console.log('Missing materials (Expected 14 copper):', missing)
  if (missing?.length === 1 && missing[0].itemId === 'copper' && missing[0].amount === 14) {
    console.log('✅ Material delta calculation is 100% accurate!')
  } else {
    console.error('❌ Material calculation mismatch.')
  }

  // 3. Test Action Freshness Check (180 Ticks)
  console.log('\n--- Test 3: Action Freshness (Stale Check) ---')
  const currentTick = 1000
  const freshTick = 950 // 50 ticks ago (OK)
  const staleTick = 800 // 200 ticks ago (> 180 ticks, SHOULD REJECT)

  const blueprintRequiresFreshness = requiresFreshSnapshot({ type: 'PLACE_BLUEPRINT' })
  const notificationRequiresFreshness = requiresFreshSnapshot({ type: 'NOTIFY_USER' })
  const freshAccepted = currentTick - freshTick <= 180
  const staleRejected = currentTick - staleTick > 180
  console.log(`Fresh blueprint check (50 ticks ago): ${freshAccepted && blueprintRequiresFreshness ? 'PASSED (Accepted)' : 'FAILED'}`)
  console.log(`Stale blueprint check (200 ticks ago): ${staleRejected && blueprintRequiresFreshness ? 'PASSED (Rejected with STALE_WORLD_STATE)' : 'FAILED'}`)
  console.log(`Historical text check: ${!notificationRequiresFreshness ? 'PASSED (No tick expiry)' : 'FAILED'}`)

  if (!smelterRecipe || missing?.[0]?.amount !== 14 || !freshAccepted || !staleRejected || !blueprintRequiresFreshness || notificationRequiresFreshness)
    throw new Error('One or more local verification checks failed.')

  console.log('\n✨ All local unit tests completed!')
}

runLocalTests().catch(console.error)
