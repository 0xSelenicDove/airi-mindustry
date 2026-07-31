import { createGameContext } from './context.js'
import { getGameSnapshot } from './snapshot.js'

const context = createGameContext(await getGameSnapshot())
console.log(JSON.stringify(context, null, 2))
