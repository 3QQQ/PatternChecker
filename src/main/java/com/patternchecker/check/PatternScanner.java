package com.patternchecker.check;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans every pattern reachable through an ME network (pattern providers and
 * ME storage) and reports problems:
 * <ul>
 *   <li>patterns that cannot be decoded at all (empty or corrupted),</li>
 *   <li>patterns AE2 considers invalid (missing items, removed recipes,
 *       recipe no longer matching the encoded grid),</li>
 *   <li>patterns with no output,</li>
 *   <li>patterns whose encoded operation appears more than once,</li>
 *   <li>patterns whose inputs are neither stocked nor craftable.</li>
 * </ul>
 */
public final class PatternScanner {

    /**
     * One scanned pattern with its own list of issues. Used by the tool panel
     * to list each broken pattern exactly once.
     */
    public record ScannedPattern(String itemId, Component name, Component outputDesc, Component inputDesc,
                                 String location, BlockPos pos, int slot,
                                 List<PatternIssue> issues) {
    }

    private record DuplicateInput(List<GenericStack> possibleInputs, long multiplier) {
    }

    private record DuplicateSignature(String patternType, List<DuplicateInput> inputs,
                                      List<GenericStack> outputs) {
    }

    private record DuplicateCandidate(DuplicateSignature signature, ScannedPattern pattern, long copies) {
    }

    private record InputIssueCandidate(PatternIssue issue, List<PatternIssue> ownerIssues,
                                       IPatternDetails.IInput input) {
    }

    public record ScanResult(int totalPatterns, int providerPatterns, int containerPatterns, int storagePatterns,
                             List<Component> verdicts, List<ScannedPattern> patterns, List<PatternIssue> issues) {
        public int errorCount() {
            return (int) issues.stream().filter(i -> i.type() == PatternIssue.Type.ERROR).count();
        }

        public int warningCount() {
            return issues.size() - errorCount();
        }
    }

    private PatternScanner() {
    }

    public static ScanResult scanGrid(IGrid grid, Level level) {
        List<PatternIssue> issues = new ArrayList<>();
        List<Component> verdicts = new ArrayList<>();
        List<ScannedPattern> patterns = new ArrayList<>();
        List<DuplicateCandidate> duplicateCandidates = new ArrayList<>();
        List<InputIssueCandidate> inputIssueCandidates = new ArrayList<>();
        Set<AEKey> scannedCraftingOutputs = new HashSet<>();
        int[] totals = new int[3]; // total, provider, container
        int storagePatterns = 0;

        // Canonical AE2 API: every pattern-holding grid machine implements
        // PatternContainer - the same enumeration AE2's own pattern access
        // terminal uses. This covers any addon automatically.
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PatternContainer container : grid.getMachines(PatternContainer.class)) {
            if (seen.add(container)) {
                scanPatternInventory(container, grid, level, issues, verdicts, patterns,
                        duplicateCandidates, inputIssueCandidates, scannedCraftingOutputs, totals);
            }
        }
        // Fallback for machines that expose a pattern inventory without
        // implementing PatternContainer (e.g. AE2LT matrix ports).
        for (IGridNode node : grid.getNodes()) {
            Object owner = node.getOwner();
            if (seen.add(owner) && owner instanceof BlockEntity) {
                scanPatternInventory(owner, grid, level, issues, verdicts, patterns,
                        duplicateCandidates, inputIssueCandidates, scannedCraftingOutputs, totals);
            }
        }

        // Patterns stored in ME storage (e.g. in cells, usable through the pattern access terminal).
        MEStorage storage = grid.getStorageService().getInventory();
        String storageLocation = Component.translatable("patternchecker.location.storage").getString();
        for (var entry : storage.getAvailableStacks()) {
            AEKey key = entry.getKey();
            if (key instanceof AEItemKey itemKey && PatternDetailsHelper.isEncodedPattern(itemKey.toStack())) {
                long copies = Math.max(1L, entry.getLongValue());
                totals[0] = saturatedAdd(totals[0], copies);
                storagePatterns = saturatedAdd(storagePatterns, copies);
                check(itemKey.toStack(), grid, level, storageLocation, null, -1, null,
                        issues, verdicts, patterns, duplicateCandidates,
                        inputIssueCandidates, scannedCraftingOutputs, copies);
            }
        }

        removeCraftableInputIssues(inputIssueCandidates, scannedCraftingOutputs, issues, level);
        markDuplicatePatterns(duplicateCandidates, issues);
        return new ScanResult(totals[0], totals[1], totals[2], storagePatterns, verdicts, patterns, issues);
    }

    /**
     * Scans a single pattern container that is not attached to any discovered
     * grid (e.g. AE2LT wireless pattern providers). If it wirelessly connects
     * to a network, that network is used as the check context.
     */
    public static ScanResult scanLooseContainer(BlockEntity be, Level level) {
        List<PatternIssue> issues = new ArrayList<>();
        List<Component> verdicts = new ArrayList<>();
        List<ScannedPattern> patterns = new ArrayList<>();
        List<DuplicateCandidate> duplicateCandidates = new ArrayList<>();
        List<InputIssueCandidate> inputIssueCandidates = new ArrayList<>();
        Set<AEKey> scannedCraftingOutputs = new HashSet<>();
        int[] totals = new int[3];
        IGrid context = null;
        if (level instanceof ServerLevel serverLevel) {
            context = WirelessHelper.resolveGrid(serverLevel, be);
        }
        scanPatternInventory(be, context, level, issues, verdicts, patterns,
                duplicateCandidates, inputIssueCandidates, scannedCraftingOutputs, totals);
        removeCraftableInputIssues(inputIssueCandidates, scannedCraftingOutputs, issues, level);
        markDuplicatePatterns(duplicateCandidates, issues);
        return new ScanResult(totals[0], totals[1], totals[2], 0, verdicts, patterns, issues);
    }

    private static void scanPatternInventory(Object owner, IGrid grid, Level level,
                                             List<PatternIssue> issues, List<Component> verdicts,
                                             List<ScannedPattern> patterns,
                                             List<DuplicateCandidate> duplicateCandidates,
                                             List<InputIssueCandidate> inputIssueCandidates,
                                             Set<AEKey> scannedCraftingOutputs,
                                             int[] totals) {
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(owner);
        if (inv == null || !(owner instanceof BlockEntity be)) {
            return;
        }
        // Wireless containers (AE2LT overloaded providers) belong to the main
        // network they connect to, not to their own (possibly empty) grid.
        IGrid checkGrid = grid;
        if (level instanceof ServerLevel serverLevel) {
            IGrid resolved = WirelessHelper.resolveGrid(serverLevel, owner);
            if (resolved != null) {
                checkGrid = resolved;
            }
        }
        BlockPos pos = be.getBlockPos();
        boolean provider = owner instanceof PatternProviderLogicHost;
        String location = Component.translatable(
                provider ? "patternchecker.location.provider" : "patternchecker.location.container",
                pos.toShortString()).getString();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            totals[0]++;
            if (provider) {
                totals[1]++;
            } else {
                totals[2]++;
            }
            check(stack, checkGrid, level, location, pos, slot, provider ? (PatternProviderLogicHost) owner : null,
                    issues, verdicts, patterns, duplicateCandidates,
                    inputIssueCandidates, scannedCraftingOutputs, 1);
        }
    }

    private static void check(ItemStack stack, IGrid grid, Level level, String location, BlockPos pos,
                              int slot, PatternProviderLogicHost host,
                              List<PatternIssue> allIssues, List<Component> verdicts,
                              List<ScannedPattern> patterns,
                              List<DuplicateCandidate> duplicateCandidates,
                              List<InputIssueCandidate> inputIssueCandidates,
                              Set<AEKey> scannedCraftingOutputs, long copies) {
        List<PatternIssue> issues = new ArrayList<>();
        Component patternName = stack.getHoverName();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Component outputDesc = Component.empty();
        Component inputDesc = Component.empty();

        // Explicitly flag blank/unencoded patterns sitting in provider slots.
        if (!PatternDetailsHelper.isEncodedPattern(stack)
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("pattern")) {
            issues.add(new PatternIssue(PatternIssue.Type.ERROR, PatternIssue.Category.BROKEN,
                    message("patternchecker.issue.blankPattern", patternName, location, null), pos, location));
            verdicts.add(verdictLine("patternchecker.verdict.broken", patternName, location,
                    "blank/unencoded"));
            patterns.add(new ScannedPattern(itemId, patternName, outputDesc, inputDesc, location, pos, slot, issues));
            allIssues.addAll(issues);
            return;
        }

        // Decoding is AE2's own gate: it throws for missing content, removed
        // recipes or recipes that no longer match the encoded grid.
        IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(stack, level);
        } catch (Exception e) {
            String detail = e.getMessage();
            String key = detail != null && detail.toLowerCase().contains("missing content")
                    ? "patternchecker.issue.missingContent"
                    : "patternchecker.issue.invalid";
            MutableComponent message = message(key, patternName, location, detail);
            issues.add(new PatternIssue(PatternIssue.Type.ERROR, PatternIssue.Category.BROKEN, message, pos, location));
            verdicts.add(verdictLine("patternchecker.verdict.broken", patternName, location, e.getMessage()));
            return;
        }
        if (details == null) {
            issues.add(new PatternIssue(PatternIssue.Type.ERROR, PatternIssue.Category.BROKEN,
                    message("patternchecker.issue.undecodable", patternName, location, null), pos, location));
            verdicts.add(verdictLine("patternchecker.verdict.broken", patternName, location, null));
            return;
        }
        outputDesc = describeOutputs(details);
        inputDesc = describeInputs(details);

        boolean processing = details instanceof AEProcessingPattern;
        if (processing) {
            if (hasFluidIO(details)) {
                // Item-based recipe matching cannot judge fluid recipes.
                verdicts.add(verdictLine("patternchecker.verdict.processing.fluid", patternName, location, null));
                checkProcessingPattern(details, patternName, location, pos, issues,
                        hasCraftingOnlyTarget(host, level, pos)
                                ? ProcessingMachineResult.WRONG_MACHINE
                                : ProcessingMachineResult.UNKNOWN);
            } else {
                ProcessingMachineResult result = checkProcessingMachine(host, level, pos, details);
                String verdictKey = switch (result) {
                    case MATCH, UNKNOWN -> "patternchecker.verdict.processing.recipe";
                    case NO_TARGET -> "patternchecker.verdict.processing.noTarget";
                    case WRONG_MACHINE -> "patternchecker.verdict.processing.wrongMachine";
                    case NO_RECIPE -> "patternchecker.verdict.processing.noRecipe";
                };
                verdicts.add(verdictLine(verdictKey, patternName, location, null));
                checkProcessingPattern(details, patternName, location, pos, issues, result);
            }
        } else {
            addCraftingOutputs(details, scannedCraftingOutputs);
            // Decoding already validated that a crafting/stonecutting/smithing
            // recipe still exists and matches, so the pattern is craftable.
            verdicts.add(verdictLine("patternchecker.verdict.craftable", patternName, location, null));
            // Safety net: if the recipe was changed so much that no current
            // recipe matches the encoded pattern anymore, flag it.
            if (!hasCurrentRecipeMatch(level, details)) {
                issues.add(new PatternIssue(PatternIssue.Type.WARNING, PatternIssue.Category.BROKEN,
                        message("patternchecker.issue.recipeChanged", patternName, location, null),
                        pos, location));
            }
        }

        if (details.getOutputs().isEmpty()) {
            issues.add(new PatternIssue(PatternIssue.Type.ERROR, PatternIssue.Category.BROKEN,
                    message("patternchecker.issue.nooutput", patternName, location, null), pos, location));
        }

        // Check that every input is stocked on the network or craftable.
        KeyCounter stored = grid.getStorageService().getInventory().getAvailableStacks();
        ICraftingService crafting = grid.getCraftingService();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0) {
                issues.add(new PatternIssue(PatternIssue.Type.WARNING, PatternIssue.Category.INPUT,
                        message("patternchecker.issue.input.empty", patternName, location, null), pos, location));
                continue;
            }

            boolean obtainable = false;
            for (GenericStack candidate : possible) {
                if (candidate == null || candidate.what() == null) {
                    continue;
                }
                long required = Math.max(1, candidate.amount() * input.getMultiplier());
                if (stored.get(candidate.what()) >= required
                        || isCraftableInput(crafting, input, candidate.what(), level)) {
                    obtainable = true;
                    break;
                }
            }

            if (!obtainable) {
                MutableComponent missing = Component.empty();
                boolean first = true;
                for (GenericStack candidate : possible) {
                    if (candidate == null || candidate.what() == null) {
                        continue;
                    }
                    if (!first) {
                        missing.append(Component.literal(" / "));
                    }
                    missing.append(candidate.what().getDisplayName());
                    first = false;
                }
                PatternIssue missingIssue = new PatternIssue(
                        PatternIssue.Type.WARNING, PatternIssue.Category.INPUT,
                        Component.translatable("patternchecker.issue.input.missing", missing)
                                .append(Component.literal(" - "))
                                .append(patternName)
                                .append(Component.literal(" @ "))
                                .append(Component.literal(location)),
                        pos, location);
                issues.add(missingIssue);
                inputIssueCandidates.add(new InputIssueCandidate(missingIssue, issues, input));
            }
        }

        ScannedPattern scannedPattern =
                new ScannedPattern(itemId, patternName, outputDesc, inputDesc, location, pos, slot, issues);
        patterns.add(scannedPattern);
        duplicateCandidates.add(new DuplicateCandidate(
                duplicateSignature(details), scannedPattern, Math.max(1L, copies)));
        allIssues.addAll(issues);
    }

    private static boolean isCraftableInput(ICraftingService crafting, IPatternDetails.IInput input,
                                            AEKey key, Level level) {
        if (crafting.isCraftable(key)) {
            return true;
        }
        try {
            return crafting.getFuzzyCraftable(key, craftable -> input.isValid(craftable, level)) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void addCraftingOutputs(IPatternDetails details, Set<AEKey> outputs) {
        for (GenericStack output : details.getOutputs()) {
            if (output != null && output.what() != null) {
                outputs.add(output.what());
            }
        }
    }

    private static void removeCraftableInputIssues(List<InputIssueCandidate> candidates,
                                                   Set<AEKey> craftingOutputs,
                                                   List<PatternIssue> allIssues, Level level) {
        if (craftingOutputs.isEmpty()) {
            return;
        }
        for (InputIssueCandidate candidate : candidates) {
            boolean craftable = false;
            for (AEKey output : craftingOutputs) {
                try {
                    if (candidate.input().isValid(output, level)) {
                        craftable = true;
                        break;
                    }
                } catch (RuntimeException ignored) {
                }
            }
            if (craftable) {
                candidate.ownerIssues().remove(candidate.issue());
                allIssues.remove(candidate.issue());
            }
        }
    }

    private static DuplicateSignature duplicateSignature(IPatternDetails details) {
        List<DuplicateInput> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            List<GenericStack> possibleInputs = new ArrayList<>();
            GenericStack[] possible = input.getPossibleInputs();
            if (possible != null) {
                for (GenericStack candidate : possible) {
                    if (candidate != null) {
                        possibleInputs.add(candidate);
                    }
                }
            }
            inputs.add(new DuplicateInput(List.copyOf(possibleInputs), input.getMultiplier()));
        }

        List<GenericStack> outputs = new ArrayList<>();
        for (GenericStack output : details.getOutputs()) {
            if (output != null) {
                outputs.add(output);
            }
        }
        return new DuplicateSignature(
                details.getClass().getName(), List.copyOf(inputs), List.copyOf(outputs));
    }

    private static void markDuplicatePatterns(List<DuplicateCandidate> candidates,
                                              List<PatternIssue> allIssues) {
        Map<DuplicateSignature, List<DuplicateCandidate>> groups = new LinkedHashMap<>();
        for (DuplicateCandidate candidate : candidates) {
            groups.computeIfAbsent(candidate.signature(), ignored -> new ArrayList<>()).add(candidate);
        }

        for (List<DuplicateCandidate> group : groups.values()) {
            long totalCopies = 0;
            for (DuplicateCandidate candidate : group) {
                totalCopies = saturatedAdd(totalCopies, candidate.copies());
            }
            if (totalCopies < 2) {
                continue;
            }

            for (DuplicateCandidate candidate : group) {
                ScannedPattern pattern = candidate.pattern();
                PatternIssue issue = new PatternIssue(
                        PatternIssue.Type.WARNING,
                        PatternIssue.Category.DUPLICATE,
                        duplicateMessage(totalCopies, pattern),
                        pattern.pos(),
                        pattern.location());
                pattern.issues().add(issue);
                allIssues.add(issue);
            }
        }
    }

    private static MutableComponent duplicateMessage(long copies, ScannedPattern pattern) {
        return Component.translatable("patternchecker.issue.duplicate", copies)
                .append(Component.literal(" - "))
                .append(pattern.name())
                .append(Component.literal(" @ "))
                .append(Component.literal(pattern.location()));
    }

    private static int saturatedAdd(int value, long amount) {
        return (int) Math.min(Integer.MAX_VALUE, (long) value + Math.max(0L, amount));
    }

    private static long saturatedAdd(long value, long amount) {
        long positiveAmount = Math.max(0L, amount);
        return Long.MAX_VALUE - value < positiveAmount ? Long.MAX_VALUE : value + positiveAmount;
    }

    private static Component describeOutputs(IPatternDetails details) {
        MutableComponent desc = Component.empty();
        int count = 0;
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.what() == null) {
                continue;
            }
            if (count > 0) {
                desc.append(Component.literal("、"));
            }
            desc.append(output.what().getDisplayName()).append(Component.literal(" ×" + output.amount()));
            if (++count >= 2) {
                break;
            }
        }
        return desc;
    }

    private static Component describeInputs(IPatternDetails details) {
        MutableComponent desc = Component.empty();
        int count = 0;
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0 || possible[0] == null || possible[0].what() == null) {
                continue;
            }
            if (count > 0) {
                desc.append(Component.literal("、"));
            }
            long amount = Math.max(1, possible[0].amount() * input.getMultiplier());
            desc.append(possible[0].what().getDisplayName()).append(Component.literal(" ×" + amount));
            if (++count >= 3) {
                break;
            }
        }
        return desc;
    }

    private static void checkProcessingPattern(IPatternDetails details, Component patternName, String location,
                                               BlockPos pos, List<PatternIssue> issues,
                                               ProcessingMachineResult machineResult) {
        String issueKey = switch (machineResult) {
            case NO_TARGET -> "patternchecker.issue.processing.noTarget";
            case WRONG_MACHINE -> "patternchecker.issue.processing.wrongMachine";
            case NO_RECIPE -> "patternchecker.issue.processing.noRecipe";
            case MATCH, UNKNOWN -> null;
        };
        if (issueKey != null) {
            PatternIssue.Type issueType = machineResult == ProcessingMachineResult.WRONG_MACHINE
                    ? PatternIssue.Type.ERROR
                    : PatternIssue.Type.WARNING;
            issues.add(new PatternIssue(issueType, PatternIssue.Category.MACHINE,
                    message(issueKey, patternName, location, null), pos, location));
        }

        // Input/output amounts must be positive.
        for (IPatternDetails.IInput input : details.getInputs()) {
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.amount() <= 0) {
                    issues.add(new PatternIssue(PatternIssue.Type.ERROR, PatternIssue.Category.BROKEN,
                            message("patternchecker.issue.processing.zeroInput", patternName, location, null),
                            pos, location));
                }
            }
        }
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0) {
                issues.add(new PatternIssue(PatternIssue.Type.ERROR, PatternIssue.Category.BROKEN,
                        message("patternchecker.issue.processing.zeroOutput", patternName, location, null),
                        pos, location));
            }
        }

        // An output identical to an input usually means a misconfigured loop.
        Set<AEKey> inputKeys = new HashSet<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() != null) {
                    inputKeys.add(candidate.what());
                }
            }
        }
        for (GenericStack output : details.getOutputs()) {
            if (output != null && output.what() != null && inputKeys.contains(output.what())) {
                issues.add(new PatternIssue(PatternIssue.Type.WARNING, PatternIssue.Category.BROKEN,
                        message("patternchecker.issue.processing.selfLoop", patternName, location, null),
                        pos, location));
                break;
            }
        }

    }

    /**
     * Looks through the installed recipe registry for a non-crafting recipe
     * whose ingredients are satisfied by the pattern's inputs and whose result
     * matches one of the pattern's outputs.
     */
    private static boolean hasMatchingMachineRecipe(Level level, IPatternDetails details, RecipeType<?> onlyType) {
        List<PatternInput> inputs = patternInputs(details);
        if (inputs.isEmpty()) {
            return false;
        }

        var registryAccess = level.registryAccess();
        for (RecipeHolder<?> holder : level.getRecipeManager().getOrderedRecipes()) {
            if (holder.value() instanceof CraftingRecipe) {
                continue;
            }
            if (onlyType != null && holder.value().getType() != onlyType) {
                continue;
            }
            boolean inputsMatch = true;
            for (RecipeRequirement requirement : recipeRequirements(holder.value())) {
                long have = 0;
                for (PatternInput patternInput : inputs) {
                    if (requirement.ingredient().test(patternInput.key().toStack())) {
                        have += patternInput.amount();
                    }
                }
                if (have < requirement.amount()) {
                    inputsMatch = false;
                    break;
                }
            }
            if (!inputsMatch) {
                continue;
            }
            List<ItemStack> candidateOutputs = recipeOutputs(holder.value(), registryAccess);
            if (candidateOutputs.isEmpty()) {
                continue;
            }
            for (GenericStack output : details.getOutputs()) {
                if (output == null || !(output.what() instanceof AEItemKey outputKey)) {
                    continue;
                }
                for (ItemStack candidate : candidateOutputs) {
                    if (candidate.getItem() == outputKey.getItem()
                            && output.amount() >= Math.max(1, candidate.getCount())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * All output items of a recipe: the standard result plus, for
     * multi-output recipes (e.g. Productive Bees centrifuges), every output
     * read reflectively via getRecipeOutputs(). This makes secondary/chance
     * outputs match too.
     */
    private static List<ItemStack> recipeOutputs(Recipe<?> recipe, net.minecraft.core.HolderLookup.Provider registryAccess) {
        List<ItemStack> outputs = new ArrayList<>();
        ItemStack result = recipe.getResultItem(registryAccess);
        if (!result.isEmpty()) {
            outputs.add(result);
        }
        // Ender IO machine recipes: getResultStacks() returns every output
        // (including chance outputs) as OutputStack instances.
        try {
            Method getResultStacks = recipe.getClass().getMethod("getResultStacks", net.minecraft.core.RegistryAccess.class);
            Object stacks = getResultStacks.invoke(recipe, registryAccess);
            if (stacks instanceof List<?> list) {
                for (Object stack : list) {
                    ItemStack out = outputStackFrom(stack);
                    if (!out.isEmpty() && !containsItem(outputs, out)) {
                        outputs.add(out);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        // Ender IO alternatives: outputs() returns OutputItems with getItemStack().
        try {
            Method getOutputs = recipe.getClass().getMethod("outputs");
            Object list = getOutputs.invoke(recipe);
            if (list instanceof List<?> entries) {
                for (Object entry : entries) {
                    if (entry == null) {
                        continue;
                    }
                    ItemStack out = outputStackFrom(entry);
                    if (out.isEmpty()) {
                        try {
                            Method getItemStack = entry.getClass().getMethod("getItemStack");
                            Object itemStackResult = getItemStack.invoke(entry);
                            if (itemStackResult instanceof ItemStack stack && !stack.isEmpty()) {
                                out = stack;
                            }
                        } catch (ReflectiveOperationException ignored2) {
                        }
                    }
                    if (!out.isEmpty() && !containsItem(outputs, out)) {
                        outputs.add(out);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        // Mekanism machine recipes: getOutputDefinition() returns the list of
        // all possible outputs.
        try {
            Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
            Object defs = getOutputDefinition.invoke(recipe);
            if (defs instanceof List<?> list) {
                for (Object def : list) {
                    if (def instanceof ItemStack stack && !stack.isEmpty() && !containsItem(outputs, stack)) {
                        outputs.add(stack);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method getOutputs = recipe.getClass().getMethod("getRecipeOutputs");
            Object map = getOutputs.invoke(recipe);
            if (map instanceof Map<?, ?> outputMap) {
                for (Object key : outputMap.keySet()) {
                    if (key instanceof ItemStack stack && !stack.isEmpty()) {
                        outputs.add(stack);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return outputs;
    }

    private static boolean containsItem(List<ItemStack> outputs, ItemStack candidate) {
        for (ItemStack existing : outputs) {
            if (existing.is(candidate.getItem())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unwraps an Ender IO OutputStack (getItem()) or a plain ItemStack.
     */
    private static ItemStack outputStackFrom(Object stack) {
        if (stack instanceof ItemStack itemStack) {
            return itemStack;
        }
        try {
            Method getItem = stack.getClass().getMethod("getItem");
            Object result = getItem.invoke(stack);
            if (result instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return ItemStack.EMPTY;
    }

    private record PatternInput(AEItemKey key, long amount) {
    }

    private record RecipeRequirement(Ingredient ingredient, long amount) {
    }

    private static List<PatternInput> patternInputs(IPatternDetails details) {
        List<PatternInput> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() instanceof AEItemKey key) {
                    inputs.add(new PatternInput(key, Math.max(1, candidate.amount() * input.getMultiplier())));
                }
            }
        }
        return inputs;
    }

    /**
     * Extracts ingredient requirements from a recipe. Standard recipes use
     * getIngredients() (one per slot); ExtendedAE-style recipes expose them via
     * getInputs() (IngredientStack$Item with ingredient + amount), read
     * reflectively so no hard dependency is needed.
     */
    private static List<RecipeRequirement> recipeRequirements(Recipe<?> recipe) {
        List<RecipeRequirement> requirements = new ArrayList<>();
        // Ender IO-style: inputs() returns List<SizedIngredient> with counts.
        try {
            Method getInputs = recipe.getClass().getMethod("inputs");
            Object list = getInputs.invoke(recipe);
            if (list instanceof List<?> entries) {
                for (Object entry : entries) {
                    RecipeRequirement requirement = sizedIngredientFrom(entry);
                    if (requirement != null) {
                        requirements.add(requirement);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        if (requirements.isEmpty()) {
            try {
                // Mekanism-style: getInput() returns an ItemStackIngredient
                // wrapping a SizedIngredient (ingredient + count).
                Method getInput = recipe.getClass().getMethod("getInput");
                Object input = getInput.invoke(recipe);
                RecipeRequirement requirement = sizedIngredientFrom(input);
                if (requirement != null) {
                    requirements.add(requirement);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (requirements.isEmpty()) {
            try {
                // ExtendedAE-style: getInputs() returns IngredientStack items.
                Method getInputs = recipe.getClass().getMethod("getInputs");
                Object list = getInputs.invoke(recipe);
                if (list instanceof List<?> entries) {
                    for (Object entry : entries) {
                        if (entry == null) {
                            continue;
                        }
                        Method getIngredient = entry.getClass().getMethod("getIngredient");
                        Method getAmount = entry.getClass().getMethod("getAmount");
                        Object ingredient = getIngredient.invoke(entry);
                        Object amount = getAmount.invoke(entry);
                        if (ingredient instanceof Ingredient ing && amount instanceof Integer count && count > 0) {
                            requirements.add(new RecipeRequirement(ing, count));
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (requirements.isEmpty()) {
            // AE2 Crystal Science-style: inputA()/inputB()/inputC() (or their
            // getInputA()/getInputB()/getInputC() variants) return SizedIngredient.
            try {
                for (String name : new String[]{"inputA", "inputB", "inputC",
                        "getInputA", "getInputB", "getInputC"}) {
                    try {
                        Method getter = recipe.getClass().getMethod(name);
                        RecipeRequirement requirement = sizedIngredientFrom(getter.invoke(recipe));
                        if (requirement != null) {
                            requirements.add(requirement);
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (requirements.isEmpty()) {
            // Standard recipes: getIngredients() (one per slot, count 1).
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient != null && !ingredient.isEmpty()) {
                    requirements.add(new RecipeRequirement(ingredient, 1));
                }
            }
        }
        return requirements;
    }

    /**
     * Unwraps a SizedIngredient (NeoForge: ingredient() + count()), including
     * Mekanism's ItemStackIngredient wrapper and Ender IO's SizedIngredient.
     */
    private static RecipeRequirement sizedIngredientFrom(Object input) {
        if (input == null) {
            return null;
        }
        try {
            // Mekanism ItemStackIngredient wraps a SizedIngredient via ingredient().
            Object sized = input;
            Method wrapperIngredient = input.getClass().getMethod("ingredient");
            sized = wrapperIngredient.invoke(input);
            Method ingredientGetter = sized.getClass().getMethod("ingredient");
            Method countGetter = sized.getClass().getMethod("count");
            Object ingredient = ingredientGetter.invoke(sized);
            Object count = countGetter.invoke(sized);
            if (ingredient instanceof Ingredient ing && !ing.isEmpty() && count instanceof Integer c && c > 0) {
                return new RecipeRequirement(ing, c);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    /**
     * Safety net for crafting/stonecutting/smithing patterns: checks whether
     * ANY current recipe (including crafting) still matches the encoded
     * inputs and output. Catches patterns that survived decoding but whose
     * recipe was changed beyond recognition.
     */
    private static boolean hasCurrentRecipeMatch(Level level, IPatternDetails details) {
        List<ItemStack> inputStacks = patternInputStacks(details);
        if (inputStacks.isEmpty()) {
            return true;
        }
        var registryAccess = level.registryAccess();
        for (RecipeHolder<?> holder : level.getRecipeManager().getOrderedRecipes()) {
            boolean inputsMatch = true;
            for (Ingredient ingredient : holder.value().getIngredients()) {
                // Skip empty cells of shaped recipes - they must not be matched.
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                boolean any = false;
                for (ItemStack stack : inputStacks) {
                    if (ingredient.test(stack)) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    inputsMatch = false;
                    break;
                }
            }
            if (!inputsMatch) {
                continue;
            }
            ItemStack result = holder.value().getResultItem(registryAccess);
            for (GenericStack output : details.getOutputs()) {
                if (output != null && output.what() instanceof AEItemKey outputKey
                        && result.getItem() == outputKey.getItem()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<ItemStack> patternInputStacks(IPatternDetails details) {
        List<ItemStack> inputStacks = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() instanceof AEItemKey key) {
                    inputStacks.add(key.toStack());
                }
            }
        }
        return inputStacks;
    }

    private enum ProcessingMachineResult {
        MATCH,
        NO_TARGET,
        WRONG_MACHINE,
        NO_RECIPE,
        UNKNOWN
    }

    /**
     * Checks the processing pattern against the provider's actual push target.
     * A recipe found elsewhere in the registry is not enough: when the target
     * machine type is known, the recipe must belong to that type.
     */
    private static ProcessingMachineResult checkProcessingMachine(PatternProviderLogicHost host, Level level,
                                                                  BlockPos pos, IPatternDetails details) {
        if (host == null || pos == null) {
            // Patterns in ME storage are not assigned to a provider yet.
            return ProcessingMachineResult.UNKNOWN;
        }

        Set<RecipeType<?>> types = machineRecipeTypes(host, level, pos);
        boolean wireless = level instanceof ServerLevel serverLevel
                && !WirelessHelper.resolveConnectionTargets(serverLevel, host).isEmpty();
        boolean hasTarget = hasPhysicalTarget(host, level, pos) || wireless;
        boolean craftingOnly = hasCraftingOnlyTarget(host, level, pos);

        if (com.patternchecker.PatternCheckerMod.LOGGER.isDebugEnabled()) {
            com.patternchecker.PatternCheckerMod.LOGGER.debug(
                    "checkProcessingMachine at {}: target={}, craftingOnly={}, recipeTypes={}",
                    pos.toShortString(), hasTarget, craftingOnly, types.size());
            for (RecipeType<?> type : types) {
                com.patternchecker.PatternCheckerMod.LOGGER.debug("  -> recipe type: {}", type);
            }
        }

        if (!hasTarget) {
            return ProcessingMachineResult.NO_TARGET;
        }
        // Molecular assemblers accept AE2 crafting plans, but processing
        // patterns are not crafting recipes and can never run in them.
        if (craftingOnly && types.isEmpty()) {
            return ProcessingMachineResult.WRONG_MACHINE;
        }

        if (!types.isEmpty()) {
            for (RecipeType<?> type : types) {
                if (hasMatchingMachineRecipe(level, details, type)) {
                    return ProcessingMachineResult.MATCH;
                }
            }
            // The inputs/outputs form a valid machine recipe, but not for the
            // machine this provider is actually facing.
            return hasMatchingMachineRecipe(level, details, null)
                    ? ProcessingMachineResult.WRONG_MACHINE
                    : ProcessingMachineResult.NO_RECIPE;
        }

        // Unknown addon targets cannot be mapped to a recipe type without a
        // hard dependency. Preserve the existing permissive behavior for them.
        if (hasCraftingMachine(host, level, pos)) {
            return ProcessingMachineResult.UNKNOWN;
        }
        return hasMatchingMachineRecipe(level, details, null)
                ? ProcessingMachineResult.UNKNOWN
                : ProcessingMachineResult.NO_RECIPE;
    }

    private static boolean hasPhysicalTarget(PatternProviderLogicHost host, Level level, BlockPos pos) {
        for (Direction direction : host.getTargets()) {
            if (!level.getBlockState(pos.relative(direction)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * True for machines that only execute crafting patterns.
     */
    private static boolean hasCraftingOnlyTarget(PatternProviderLogicHost host, Level level, BlockPos pos) {
        if (host == null || pos == null) {
            return false;
        }
        for (Direction direction : host.getTargets()) {
            if (isCraftingOnlyBlock(level.getBlockState(pos.relative(direction)).getBlock())) {
                return true;
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            for (BlockPos target : WirelessHelper.resolveConnectionTargets(serverLevel, host)) {
                if (isCraftingOnlyBlock(level.getBlockState(target).getBlock())) {
                    return true;
                }
                for (Direction direction : Direction.values()) {
                    if (isCraftingOnlyBlock(level.getBlockState(target.relative(direction)).getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isCraftingOnlyBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals("ae2") && id.getPath().equals("molecular_assembler");
    }

    private static boolean hasFluidIO(IPatternDetails details) {
        for (IPatternDetails.IInput input : details.getInputs()) {
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() instanceof AEFluidKey) {
                    return true;
                }
            }
        }
        for (GenericStack output : details.getOutputs()) {
            if (output != null && output.what() instanceof AEFluidKey) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recipe types of known vanilla machines, local and remote (wireless).
     */
    private static Set<RecipeType<?>> machineRecipeTypes(PatternProviderLogicHost host, Level level, BlockPos pos) {
        Set<RecipeType<?>> types = new HashSet<>();
        if (host != null && pos != null) {
            for (var direction : host.getTargets()) {
                addMachineType(level.getBlockState(pos.relative(direction)).getBlock(), types);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            for (BlockPos target : WirelessHelper.resolveConnectionTargets(serverLevel, host)) {
                for (var direction : Direction.values()) {
                    addMachineType(level.getBlockState(target.relative(direction)).getBlock(), types);
                }
            }
        }
        return types;
    }

    private static void addMachineType(Block block, Set<RecipeType<?>> types) {
        if (block instanceof FurnaceBlock) {
            types.add(RecipeType.SMELTING);
        } else if (block instanceof BlastFurnaceBlock) {
            types.add(RecipeType.BLASTING);
        } else if (block instanceof SmokerBlock) {
            types.add(RecipeType.SMOKING);
        } else if (block instanceof CampfireBlock) {
            types.add(RecipeType.CAMPFIRE_COOKING);
        } else if (block instanceof StonecutterBlock) {
            types.add(RecipeType.STONECUTTING);
        } else if (block instanceof SmithingTableBlock) {
            types.add(RecipeType.SMITHING);
        } else {
            // Ender IO / Mekanism machines (and their addons) identify by block
            // id; resolve their recipe types from the recipe type registry so
            // no hard dependency on those mods is required.
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            String recipeTypeId = machineRecipeTypeId(id);
            if (recipeTypeId != null) {
                RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(recipeTypeId));
                if (type != null) {
                    types.add(type);
                }
            }
        }
    }

    /**
     * Maps a machine block id to its machine recipe type id by path keyword,
     * independent of namespace, so Ender IO / Mekanism addons (which use their
     * own namespaces but the same machine names and recipe types) are covered
     * too. Returns null for unknown blocks.
     */
    private static String machineRecipeTypeId(ResourceLocation blockId) {
        String path = blockId.getPath();
        String normalized = path
                .replaceFirst("^(me_)?(basic|advanced|elite|ultimate|absolute|cosmic|infinite|creative|dense|supernova|compressed|evolved|compact)_", "")
                .replace("_factory", "")
                .replace("_machine", "");

        // Ender IO machines.
        if (normalized.endsWith("alloy_smelter") || normalized.endsWith("alloy_smelting")
                || normalized.equals("alloy_smelter") || normalized.equals("alloying")) {
            return "enderio:alloy_smelting";
        }
        if (normalized.endsWith("sag_mill") || normalized.equals("sag_milling")
                || normalized.endsWith("sag_milling")) {
            return "enderio:sag_milling";
        }
        if (normalized.endsWith("slicer") || normalized.equals("slicing")) {
            return "enderio:slicing";
        }
        if (normalized.endsWith("soul_binder") || normalized.equals("soul_binding")) {
            return "enderio:soul_binding";
        }
        if (normalized.endsWith("painter") || normalized.equals("painting")) {
            return "enderio:painting";
        }
        if (normalized.endsWith("enchanter") || normalized.equals("enchanting")) {
            return "enderio:enchanting";
        }
        if (normalized.endsWith("vat") || normalized.equals("vat_fermenting")
                || normalized.endsWith("fermenting")) {
            return "enderio:vat_fermenting";
        }

        // AE2 Crystal Science (ae2cs) machines.
        if (normalized.contains("circuit_etcher") || normalized.equals("circuit_etcher")) {
            return "ae2cs:circuit_etcher_recipe";
        }
        if (normalized.contains("crystal_aggregator") || normalized.equals("crystal_aggregator")) {
            return "ae2cs:crystal_aggregator_recipe";
        }
        if (normalized.contains("crystal_pulverizer") || normalized.contains("grindstone")
                || normalized.equals("crystal_pulverizer") || normalized.equals("quartz_grindstone")) {
            return "ae2cs:crystal_pulverizer_recipe";
        }

        // Mekanism machines (base mod + addons).
        if (normalized.contains("enriching") || normalized.contains("enrichment")) {
            return "mekanism:enriching";
        }
        if (normalized.contains("crushing") || normalized.contains("crusher")) {
            return "mekanism:crushing";
        }
        if (normalized.contains("combining") || normalized.contains("combiner")) {
            return "mekanism:combining";
        }
        if (normalized.contains("purifying") || normalized.contains("purification")) {
            return "mekanism:purifying";
        }
        if (normalized.contains("smelting") || normalized.contains("energized_smelter")) {
            // Mekanism's energized smelter uses the vanilla smelting recipe type.
            return "minecraft:smelting";
        }
        if (normalized.contains("infusing") || normalized.contains("infuser")) {
            return "mekanism:metallurgic_infusing";
        }
        if (normalized.contains("sawing") || normalized.contains("sawmill")) {
            return "mekanism:sawing";
        }
        if (normalized.contains("painting")) {
            return "mekanism:painting";
        }
        if (normalized.contains("injecting") || normalized.contains("injection")) {
            return "mekanism:injecting";
        }
        if (normalized.contains("oxidizing") || normalized.contains("oxidizer")) {
            return "mekanism:oxidizing";
        }
        if (normalized.contains("dissolution") || normalized.contains("dissolving")) {
            return "mekanism:dissolution";
        }
        if (normalized.contains("crystallizing") || normalized.contains("crystallizer")) {
            return "mekanism:crystallizing";
        }
        if (normalized.contains("chemical_infusing")) {
            return "mekanism:chemical_infusing";
        }
        if (normalized.contains("separating") || normalized.contains("separator")) {
            return "mekanism:separating";
        }
        if (normalized.contains("centrifuging") || normalized.contains("centrifuge")) {
            return "mekanism:centrifuging";
        }
        if (normalized.contains("reacting") || normalized.contains("reaction")) {
            return "mekanism:reaction";
        }
        if (normalized.contains("activating") || normalized.contains("activator")) {
            return "mekanism:activating";
        }
        if (normalized.contains("compressing") || normalized.contains("compressor")) {
            return "mekanism:compressing";
        }
        if (normalized.contains("rotary") || normalized.contains("condensentrator")) {
            return "mekanism:rotary";
        }
        if (normalized.contains("washing") || normalized.contains("washer")) {
            return "mekanism:washing";
        }
        if (normalized.contains("nucleosynthesizing") || normalized.contains("nucleosynthesizer")) {
            return "mekanism:nucleosynthesizing";
        }
        if (normalized.contains("evaporating") || normalized.contains("evaporation")) {
            return "mekanism:evaporating";
        }
        return null;
    }

    /**
     * AE2's canonical signal: a machine that accepts pushed patterns.
     */
    private static boolean hasCraftingMachine(PatternProviderLogicHost host, Level level, BlockPos pos) {
        if (host != null && pos != null) {
            for (var direction : host.getTargets()) {
                ICraftingMachine machine = ICraftingMachine.of(level, pos, direction);
                if (machine != null && machine.acceptsPlans()) {
                    return true;
                }
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            for (BlockPos target : WirelessHelper.resolveConnectionTargets(serverLevel, host)) {
                for (var direction : Direction.values()) {
                    ICraftingMachine machine = ICraftingMachine.of(level, target, direction);
                    if (machine != null && machine.acceptsPlans()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the issues to show, hiding input-supply issues unless enabled.
     */
    public static List<PatternIssue> filterIssues(List<PatternIssue> issues, boolean showInput,
                                                  boolean showDuplicates) {
        if (showInput && showDuplicates) {
            return issues;
        }
        return issues.stream()
                .filter(issue -> showInput || issue.category() != PatternIssue.Category.INPUT)
                .filter(issue -> showDuplicates || issue.category() != PatternIssue.Category.DUPLICATE)
                .toList();
    }

    /**
     * Visible error/warning counts per scan result.
     */
    public static int[] visibleCounts(ScanResult result, boolean showInput, boolean showDuplicates) {
        int errors = 0;
        int warnings = 0;
        for (ScannedPattern pattern : result.patterns()) {
            for (PatternIssue issue : filterIssues(pattern.issues(), showInput, showDuplicates)) {
                if (issue.type() == PatternIssue.Type.ERROR) {
                    errors++;
                } else {
                    warnings++;
                }
            }
        }
        return new int[]{errors, warnings};
    }

    private static MutableComponent verdictLine(String key, Component patternName, String location, String detail) {
        MutableComponent line = Component.translatable(key)
                .append(Component.literal(" - "))
                .append(patternName)
                .append(Component.literal(" @ "))
                .append(Component.literal(location));
        if (detail != null && !detail.isEmpty()) {
            line.append(Component.literal(" (")).append(Component.literal(detail)).append(Component.literal(")"));
        }
        return line;
    }

    private static MutableComponent message(String key, Component patternName, String location, String detail) {
        MutableComponent message = Component.translatable(key)
                .append(Component.literal(" - "))
                .append(patternName)
                .append(Component.literal(" @ "))
                .append(Component.literal(location));
        if (detail != null && !detail.isEmpty()) {
            message.append(Component.literal(" (")).append(Component.literal(detail)).append(Component.literal(")"));
        }
        return message;
    }
}
