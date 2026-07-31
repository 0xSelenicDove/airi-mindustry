package dev.airi.mindustry.tools;

import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.game.SpawnGroup;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.Waves;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumeLiquids;
import mindustry.world.consumers.ConsumePower;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import arc.struct.Seq;
import arc.struct.StringMap;

/**
 * Exports the authoritative base-game content from the pinned Mindustry JAR.
 * It intentionally does not load user mods: those must be exported separately
 * from the exact client/runtime where they are enabled.
 */
public final class GameDataGenerator {
    private GameDataGenerator() {}

    public static void main(String[] args) throws IOException {
        final Path output = args.length == 0
            ? Path.of("..", "..", "game-data", "v159")
            : Path.of(args[0]);
        loadBaseContent();
        Files.createDirectories(output);

        write(output.resolve("items.json"), itemsJson());
        write(output.resolve("liquids.json"), liquidsJson());
        write(output.resolve("blocks.json"), blocksJson());
        write(output.resolve("recipes.json"), recipesJson());
        write(output.resolve("units.json"), unitsJson());
        write(output.resolve("wave-profiles.json"), waveProfilesJson());
        write(output.resolve("blueprints.json"), blueprintsJson());
        write(output.resolve("manifest.json"), manifestJson());
        System.out.println("Exported Mindustry base content to " + output.toAbsolutePath());
    }

    private static void loadBaseContent() {
        Vars.headless = true;
        Vars.content = new ContentLoader();
        Vars.content.createBaseContent();
    }

    private static String itemsJson() {
        final List<String> items = new ArrayList<>();
        for (Item item : Vars.content.items()) {
            items.add(String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"hardness\":%d,\"cost\":%.3f,\"flammability\":%.3f,\"explosiveness\":%.3f,\"radioactivity\":%.3f,\"charge\":%.3f}",
                json(item.name), item.hardness, item.cost, item.flammability, item.explosiveness, item.radioactivity, item.charge));
        }
        return array(items);
    }

    private static String blocksJson() {
        final List<String> blocks = new ArrayList<>();
        for (Block block : Vars.content.blocks()) {
            if (block.isAir())
                continue;
            blocks.add(String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"category\":\"%s\",\"size\":%d,\"health\":%d,\"hasPower\":%s,\"consumesPower\":%s,\"outputsPower\":%s,\"requirements\":%s}",
                json(block.name), json(block.category.name()), block.size, block.health, block.hasPower,
                block.consumesPower, block.outputsPower, itemStacksJson(block.requirements)));
        }
        return array(blocks);
    }

    private static String liquidsJson() {
        final List<String> liquids = new ArrayList<>();
        for (Liquid liquid : Vars.content.liquids()) {
            liquids.add(String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"gas\":%s,\"flammability\":%.3f,\"temperature\":%.3f,\"heatCapacity\":%.3f,\"viscosity\":%.3f,\"coolant\":%s}",
                json(liquid.name), liquid.gas, liquid.flammability, liquid.temperature, liquid.heatCapacity, liquid.viscosity, liquid.coolant));
        }
        return array(liquids);
    }

    private static String recipesJson() {
        final List<String> recipes = new ArrayList<>();
        for (Block block : Vars.content.blocks()) {
            final ConsumeItems itemInput = block.findConsumer(consumer -> consumer instanceof ConsumeItems);
            final ConsumeLiquid liquidInput = block.findConsumer(consumer -> consumer instanceof ConsumeLiquid);
            final ConsumeLiquids liquidsInput = block.findConsumer(consumer -> consumer instanceof ConsumeLiquids);
            final ConsumePower powerInput = block.findConsumer(consumer -> consumer instanceof ConsumePower);
            final String itemInputs = itemInput == null ? "[]" : itemStacksJson(itemInput.items);
            final String liquidInputs = liquidInput != null ? "[" + liquidStackJson(liquidInput.liquid, liquidInput.amount) + "]"
                : liquidsInput == null ? "[]" : liquidStacksJson(liquidsInput.liquids);
            final String itemOutputs = itemOutputsJson(block);
            final String liquidOutputs = liquidOutputsJson(block);
            if (!itemInputs.equals("[]") || !liquidInputs.equals("[]") || !itemOutputs.equals("[]") || !liquidOutputs.equals("[]") || powerInput != null) {
                final float craftTime = block instanceof GenericCrafter crafter ? crafter.craftTime : 0f;
                final float powerPerTick = powerInput == null ? 0f : powerInput.usage;
                recipes.add(String.format(Locale.ROOT,
                    "{\"blockId\":\"%s\",\"itemInputs\":%s,\"liquidInputs\":%s,\"itemOutputs\":%s,\"liquidOutputs\":%s,\"craftTimeTicks\":%.2f,\"powerPerTick\":%.4f}",
                    json(block.name), itemInputs, liquidInputs, itemOutputs, liquidOutputs, craftTime, powerPerTick));
            }
        }
        return array(recipes);
    }

    private static String itemOutputsJson(Block block) {
        if (!(block instanceof GenericCrafter crafter))
            return "[]";
        final List<String> outputs = new ArrayList<>();
        if (crafter.outputItem != null)
            outputs.add(itemStackJson(crafter.outputItem));
        if (crafter.outputItems != null) {
            for (ItemStack output : crafter.outputItems)
                outputs.add(itemStackJson(output));
        }
        return array(outputs);
    }

    private static String liquidOutputsJson(Block block) {
        if (!(block instanceof GenericCrafter crafter))
            return "[]";
        final List<String> outputs = new ArrayList<>();
        if (crafter.outputLiquid != null)
            outputs.add(liquidStackJson(crafter.outputLiquid.liquid, crafter.outputLiquid.amount));
        if (crafter.outputLiquids != null) {
            for (LiquidStack output : crafter.outputLiquids)
                outputs.add(liquidStackJson(output.liquid, output.amount));
        }
        return array(outputs);
    }

    private static String unitsJson() {
        final List<String> units = new ArrayList<>();
        for (UnitType unit : Vars.content.units()) {
            units.add(String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"health\":%.2f,\"armor\":%.2f,\"speed\":%.3f,\"flying\":%s,\"targetAir\":%s,\"targetGround\":%s}",
                json(unit.name), unit.health, unit.armor, unit.speed, unit.flying, unit.targetAir, unit.targetGround));
        }
        return array(units);
    }

    /** Default generated survival profile only; maps may replace these rules at runtime. */
    private static String waveProfilesJson() {
        final List<String> groups = new ArrayList<>();
        for (SpawnGroup group : Waves.generate(0.5f)) {
            groups.add(String.format(Locale.ROOT,
                "{\"unitId\":\"%s\",\"begin\":%d,\"end\":%d,\"spacing\":%d,\"max\":%d,\"unitScaling\":%.3f,\"unitAmount\":%d,\"shields\":%.2f,\"shieldScaling\":%.3f}",
                json(group.type.name), group.begin, group.end, group.spacing, group.max, group.unitScaling,
                group.unitAmount, group.shields, group.shieldScaling));
        }
        return "{\"kind\":\"generated-default\",\"difficulty\":0.5,\"groups\":" + array(groups) + "}";
    }

    private static String manifestJson() {
        return String.format(Locale.ROOT,
            "{\"schemaVersion\":1,\"mindustryBuild\":159,\"mindustryVersion\":\"v159.7\",\"source\":\"Mindustry-v159.7.jar base content\",\"includesUserMods\":false}");
    }

    /** A small, deliberately reviewed Serpulo defensive pattern for the first action slice. */
    private static String blueprintsJson() {
        final Schematic schematic = starterDuoDefense();
        final Map<String, Integer> costs = new TreeMap<>();
        for (Schematic.Stile tile : schematic.tiles) {
            for (ItemStack cost : tile.block.requirements)
                costs.merge(cost.item.name, cost.amount, Integer::sum);
        }
        final List<String> requiredItems = new ArrayList<>();
        costs.forEach((item, amount) -> requiredItems.add("{\"itemId\":\"" + json(item) + "\",\"amount\":" + amount + "}"));
        return "[{\"id\":\"starter-duo-defense\",\"description\":\"One Duo turret, a copper-wall screen, and a small conveyor/router feed.\",\"schematicBase64\":\""
            + new Schematics().writeBase64(schematic) + "\",\"requiredItems\":" + array(requiredItems)
            + ",\"width\":7,\"height\":5}]";
    }

    private static Schematic starterDuoDefense() {
        final Seq<Schematic.Stile> tiles = new Seq<>();
        final var duo = Vars.content.block("duo");
        final var wall = Vars.content.block("copper-wall");
        final var conveyor = Vars.content.block("conveyor");
        final var router = Vars.content.block("router");
        for (int x = 0; x < 7; x += 2)
            tiles.add(new Schematic.Stile(wall, x, 4, null, (byte) 0));
        tiles.add(new Schematic.Stile(duo, 3, 2, null, (byte) 0));
        tiles.add(new Schematic.Stile(router, 3, 0, null, (byte) 0));
        tiles.add(new Schematic.Stile(conveyor, 2, 0, null, (byte) 0));
        tiles.add(new Schematic.Stile(conveyor, 4, 0, null, (byte) 0));
        return new Schematic(tiles, new StringMap(), 7, 5);
    }

    private static String itemStacksJson(ItemStack[] stacks) {
        final List<String> items = new ArrayList<>();
        if (stacks != null) {
            for (ItemStack stack : stacks)
                items.add(itemStackJson(stack));
        }
        return array(items);
    }

    private static String itemStackJson(ItemStack stack) {
        return "{\"itemId\":\"" + json(stack.item.name) + "\",\"amount\":" + stack.amount + "}";
    }

    private static String liquidStacksJson(LiquidStack[] stacks) {
        final List<String> liquids = new ArrayList<>();
        for (LiquidStack stack : stacks)
            liquids.add(liquidStackJson(stack.liquid, stack.amount));
        return array(liquids);
    }

    private static String liquidStackJson(Liquid liquid, float amount) {
        return String.format(Locale.ROOT, "{\"liquidId\":\"%s\",\"amount\":%.4f}", json(liquid.name), amount);
    }

    private static String array(List<String> values) {
        return "[" + String.join(",", values) + "]";
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void write(Path path, String json) throws IOException {
        Files.writeString(path, json + System.lineSeparator());
    }
}
