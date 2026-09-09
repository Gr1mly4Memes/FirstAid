/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Adds FirstAid healing items to vanilla chest loot tables.
 * Replicates the old single-pool behavior: each roll picks exactly one entry by weight.
 * Configured via datapack JSONs in data/firstaid/neoforge/loot_modifiers/.
 */
public class FirstAidChestLootModifier extends LootModifier {
    public static final MapCodec<FirstAidChestLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).and(
            NumberProviders.CODEC.fieldOf("rolls").forGetter(m -> m.rolls)).and(
            LootEntry.CODEC.listOf().fieldOf("entries").forGetter(m -> m.entries))
            .apply(inst, FirstAidChestLootModifier::new));

    public record LootEntry(Item item, int weight, NumberProvider count) {
        public static final Codec<LootEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(LootEntry::item),
                Codec.INT.fieldOf("weight").forGetter(LootEntry::weight),
                NumberProviders.CODEC.fieldOf("count").forGetter(LootEntry::count))
                .apply(inst, LootEntry::new));
    }

    private final NumberProvider rolls;
    private final java.util.List<LootEntry> entries;

    protected FirstAidChestLootModifier(LootItemCondition[] conditions, int priority, NumberProvider rolls, java.util.List<LootEntry> entries) {
        super(conditions, priority);
        this.rolls = rolls;
        this.entries = entries;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        int rollCount = this.rolls.getInt(context);
        if (rollCount <= 0 || entries.isEmpty()) return generatedLoot;
        int totalWeight = 0;
        for (LootEntry entry : entries) totalWeight += entry.weight();
        for (int i = 0; i < rollCount; i++) {
            int r = context.getRandom().nextInt(totalWeight);
            for (LootEntry entry : entries) {
                r -= entry.weight();
                if (r < 0) {
                    int count = entry.count().getInt(context);
                    if (count > 0) generatedLoot.add(new ItemStack(entry.item(), count));
                    break;
                }
            }
        }
        return generatedLoot;
    }
}
