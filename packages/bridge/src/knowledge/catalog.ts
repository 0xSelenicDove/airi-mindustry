import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

export interface ItemAmount { itemId: string, amount: number }
export interface LiquidAmount { liquidId: string, amount: number }
export interface BlockCatalogEntry {
  id: string
  category: string
  size: number
  health: number
  hasPower: boolean
  consumesPower: boolean
  outputsPower: boolean
  requirements: ItemAmount[]
}
export interface RecipeCatalogEntry {
  blockId: string
  itemInputs: ItemAmount[]
  liquidInputs: LiquidAmount[]
  itemOutputs: ItemAmount[]
  liquidOutputs: LiquidAmount[]
  craftTimeTicks: number
  powerPerTick: number
}
/** User-vetted schematics only. The generator never overwrites this file. */
export interface BlueprintCatalogEntry {
  id: string
  description: string
  schematicBase64: string
  requiredItems: ItemAmount[]
  width: number
  height: number
}

/** Offline-only lookup table built from the pinned Mindustry content export. */
export class GameCatalog {
  private readonly blocksById: ReadonlyMap<string, BlockCatalogEntry>
  private readonly recipesByBlockId: ReadonlyMap<string, RecipeCatalogEntry>
  private readonly blueprintsById: ReadonlyMap<string, BlueprintCatalogEntry>

  constructor(blocks: BlockCatalogEntry[], recipes: RecipeCatalogEntry[], blueprints: BlueprintCatalogEntry[]) {
    this.blocksById = new Map(blocks.map(block => [block.id, block]))
    this.recipesByBlockId = new Map(recipes.map(recipe => [recipe.blockId, recipe]))
    this.blueprintsById = new Map(blueprints.map(blueprint => [blueprint.id, blueprint]))
  }

  getBlock(id: string): BlockCatalogEntry | undefined { return this.blocksById.get(id) }
  getRecipe(blockId: string): RecipeCatalogEntry | undefined { return this.recipesByBlockId.get(blockId) }
  getBlueprint(id: string): BlueprintCatalogEntry | undefined { return this.blueprintsById.get(id) }

  /** Returns exact missing material quantities for a locally approved blueprint. */
  getMissingBlueprintItems(id: string, inventory: ReadonlyMap<string, number>): ItemAmount[] | undefined {
    const blueprint = this.getBlueprint(id)
    if (blueprint === undefined)
      return undefined
    return blueprint.requiredItems
      .map(requirement => ({ itemId: requirement.itemId, amount: Math.max(0, requirement.amount - (inventory.get(requirement.itemId) ?? 0)) }))
      .filter(requirement => requirement.amount > 0)
  }
}

/** Loads versioned JSON synchronously once at bridge startup; no model or web lookup is involved. */
export function loadV159Catalog(dataDirectory = fileURLToPath(new URL('../../../../game-data/v159/', import.meta.url))): GameCatalog {
  return new GameCatalog(
    readJson<BlockCatalogEntry[]>(dataDirectory, 'blocks.json'),
    readJson<RecipeCatalogEntry[]>(dataDirectory, 'recipes.json'),
    readJson<BlueprintCatalogEntry[]>(dataDirectory, 'blueprints.json'),
  )
}

function readJson<T>(directory: string, filename: string): T {
  const separator = directory.endsWith('/') ? '' : '/'
  return JSON.parse(readFileSync(`${directory}${separator}${filename}`, 'utf8')) as T
}
