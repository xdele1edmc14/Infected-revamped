package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.loot.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

public final class WeaponLootGuiManager {
    private static final int BACK = 45;
    private static final int CLOSE = 49;
    private static final int HELP = 53;
    private final InfectedPlugin plugin;
    private final WeaponLootRepository repository;
    private final WeaponChestService chestService;
    private final GeneratedChestService generatedChestService;
    private final WeaponSelectionListener selectionListener;
    private final Consumer<Player> openMain;
    private final Map<UUID, ChestOperationProgressBar> progressBars = new HashMap<>();

    public WeaponLootGuiManager(InfectedPlugin plugin, WeaponLootRepository repository,
                                WeaponChestService chestService, GeneratedChestService generatedChestService,
                                WeaponSelectionListener selectionListener,
                                Consumer<Player> openMain) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.chestService = Objects.requireNonNull(chestService, "chestService");
        this.generatedChestService = Objects.requireNonNull(generatedChestService, "generatedChestService");
        this.selectionListener = Objects.requireNonNull(selectionListener, "selectionListener");
        this.openMain = Objects.requireNonNull(openMain, "openMain");
    }

    public void openWizard(Player player) {
        WeaponLootCatalog catalog = repository.snapshot();
        Inventory menu = create(WeaponMenuHolder.root(WeaponMenuHolder.MenuType.WIZARD), 54,
                ChatColor.DARK_GRAY + "Weapon Chest Wizard");
        fillBorder(menu);
        List<String> regionLore = new ArrayList<>();
        regionLore.add(pointLine("Point 1", catalog.point1()));
        regionLore.add(pointLine("Point 2", catalog.point2()));
        if (catalog.point1() != null && catalog.point2() != null) {
            try {
                ChestRegion region = ChestRegion.between(catalog.point1(), catalog.point2());
                regionLore.add(ChatColor.GRAY + "Volume: " + ChatColor.WHITE + region.volume() + " blocks");
                regionLore.add(ChatColor.GRAY + "Dimensions: " + ChatColor.WHITE
                        + dimension(region.minX(), region.maxX()) + " x "
                        + dimension(region.minY(), region.maxY()) + " x "
                        + dimension(region.minZ(), region.maxZ()));
                regionLore.add(ChatColor.GRAY + "Chunks: " + ChatColor.WHITE + region.chunkCount());
                regionLore.add(ChatColor.GRAY + "Configured scan limit: " + ChatColor.WHITE
                        + catalog.settings().maxChunks());
            } catch (IllegalArgumentException exception) {
                regionLore.add(ChatColor.RED + exception.getMessage());
            }
        }
        menu.setItem(4, item(Material.MAP, ChatColor.AQUA + "Selected Chest Region", regionLore));
        menu.setItem(10, item(Material.BLAZE_ROD, ChatColor.GOLD + "Selection Wand", List.of(
                ChatColor.GRAY + "Left-click a block for point 1.",
                ChatColor.GRAY + "Right-click a block for point 2.", "", ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Receive wand")));
        menu.setItem(20, item(Material.IRON_SWORD, ChatColor.GREEN + "Guns & Ammo", List.of(
                ChatColor.GRAY + "Registered guns: " + ChatColor.WHITE + catalog.guns().size(),
                ChatColor.GRAY + "Each gun must link exact ammo.", "", ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Configure")));
        menu.setItem(24, item(Material.FIRE_CHARGE, ChatColor.RED + "Grenades", List.of(
                ChatColor.GRAY + "Registered grenades: " + ChatColor.WHITE + catalog.grenades().size(), "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Configure")));
        menu.setItem(30, chanceItem("Second Gun Chance", catalog.settings().secondGunChance()));
        menu.setItem(31, item(Material.SPYGLASS,
                ChatColor.YELLOW + "Chunk Scan Limit: " + ChatColor.WHITE + catalog.settings().maxChunks(),
                List.of(ChatColor.GRAY + "Maximum supported: " + ChatColor.WHITE + "5,000 chunks",
                        ChatColor.AQUA + "Left/Right: " + ChatColor.GRAY + "+/- 100",
                        ChatColor.AQUA + "Shift Left/Right: " + ChatColor.GRAY + "+/- 500")));
        menu.setItem(32, chanceItem("Grenade Chance", catalog.settings().grenadeChance()));
        menu.setItem(33, item(Material.CHEST,
                ChatColor.YELLOW + "Generated Chest Count: " + ChatColor.WHITE
                        + catalog.settings().generatedChestCount(),
                List.of(ChatColor.AQUA + "Left/Right: " + ChatColor.GRAY + "+/- 10",
                        ChatColor.AQUA + "Shift Left/Right: " + ChatColor.GRAY + "+/- 50",
                        ChatColor.GRAY + "Supported range: 1-1,000")));
        WeaponChestService.ActionPreview clear = chestService.preview(WeaponChestService.ActionType.CLEAR);
        WeaponChestService.ActionPreview fill = fillPreview(catalog, clear);
        GeneratedChestService.ActionPreview generate = generatedChestService.preview(
                GeneratedChestService.ActionType.GENERATE);
        GeneratedChestService.ActionPreview remove = generatedChestService.preview(
                GeneratedChestService.ActionType.REMOVE);
        if (clear.success() && clear.chestCount() >= 0) {
            regionLore.add(ChatColor.GRAY + "Discovered chests: " + ChatColor.WHITE + clear.chestCount());
            menu.setItem(4, item(Material.MAP, ChatColor.AQUA + "Selected Chest Region", regionLore));
        }
        menu.setItem(37, actionItem(Material.GOLD_BLOCK, "Generate Layout", generate));
        menu.setItem(39, actionItem(Material.LIME_CONCRETE, "Fill Loot", fill));
        menu.setItem(41, actionItem(Material.HOPPER, "Empty Loot", clear));
        menu.setItem(43, actionItem(Material.RED_CONCRETE, "Remove Generated", remove));
        menu.setItem(BACK, item(Material.ARROW, ChatColor.AQUA + "Back", List.of(ChatColor.GRAY + "Return to event control.")));
        menu.setItem(CLOSE, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));
        menu.setItem(HELP, item(Material.BOOK, ChatColor.YELLOW + "Loot Rules", List.of(
                ChatColor.GRAY + "Every chest: 1 gun + its ammo.",
                ChatColor.GRAY + "Rarely: a different second gun + ammo.",
                ChatColor.GRAY + "Optionally: one weighted grenade type.")));
        player.openInventory(menu);
    }

    public void openGuns(Player player, int requestedPage) {
        List<WeaponLootCatalog.GunEntry> entries = repository.snapshot().guns();
        int page = clampPage(requestedPage, entries.size());
        Map<Integer, UUID> targets = targets(entries.stream().map(WeaponLootCatalog.GunEntry::id).toList(), page);
        Inventory menu = create(WeaponMenuHolder.page(WeaponMenuHolder.MenuType.GUNS, page, targets), 54,
                ChatColor.DARK_GRAY + "Guns & Ammo");
        fillTopBottom(menu);
        for (var entry : entriesForPage(entries, page)) {
            int index = entries.indexOf(entry) - page * 36;
            menu.setItem(9 + index, display(entry.gun(), List.of(
                    ChatColor.GRAY + "Rarity: " + rarityColor(entry.rarity()) + entry.rarity().name(),
                    ChatColor.GRAY + "Ammo: " + (entry.ammo() == null ? ChatColor.RED + "Missing" : ChatColor.GREEN + "Linked"), "",
                    ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Edit gun and ammo")));
        }
        footer(menu, page, entries.size(), Material.HOPPER, "Drop Gun Here");
        player.openInventory(menu);
    }

    public void openGrenades(Player player, int requestedPage) {
        List<WeaponLootCatalog.GrenadeEntry> entries = repository.snapshot().grenades();
        int page = clampPage(requestedPage, entries.size());
        Map<Integer, UUID> targets = targets(entries.stream().map(WeaponLootCatalog.GrenadeEntry::id).toList(), page);
        Inventory menu = create(WeaponMenuHolder.page(WeaponMenuHolder.MenuType.GRENADES, page, targets), 54,
                ChatColor.DARK_GRAY + "Grenades");
        fillTopBottom(menu);
        for (var entry : entriesForPage(entries, page)) {
            int index = entries.indexOf(entry) - page * 36;
            menu.setItem(9 + index, display(entry.item(), List.of(
                    ChatColor.GRAY + "Rarity: " + rarityColor(entry.rarity()) + entry.rarity().name(),
                    ChatColor.GRAY + "Quantity: " + ChatColor.WHITE + entry.minQuantity() + "-" + entry.maxQuantity(), "",
                    ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Edit grenade")));
        }
        footer(menu, page, entries.size(), Material.HOPPER, "Drop Grenade Here");
        player.openInventory(menu);
    }

    private void openGunEditor(Player player, UUID id, int page) {
        Optional<WeaponLootCatalog.GunEntry> found = gun(id);
        if (found.isEmpty()) { stale(player, true, page); return; }
        var gun = found.get();
        Inventory menu = create(WeaponMenuHolder.target(WeaponMenuHolder.MenuType.GUN_EDITOR, id, page), 54,
                ChatColor.DARK_GRAY + "Edit Gun & Ammo");
        fillBorder(menu);
        menu.setItem(13, display(gun.gun(), List.of(ChatColor.GRAY + "Exact saved gun preview.")));
        menu.setItem(20, item(Material.NETHER_STAR, rarityColor(gun.rarity()) + "Rarity: " + gun.rarity().name(), adjustLore()));
        menu.setItem(22, gun.ammo() == null
                ? item(Material.HOPPER, ChatColor.RED + "Drop Corresponding Ammo Here",
                        List.of(ChatColor.GRAY + "Pick up the exact ammo stack and drop it here."))
                : display(gun.ammo(), List.of(ChatColor.GREEN + "Corresponding ammo linked.",
                        ChatColor.AQUA + "Drop another stack here to replace it.")));
        menu.setItem(24, numberItem("Minimum Ammo Bundles", gun.minAmmoBundles()));
        menu.setItem(26, numberItem("Maximum Ammo Bundles", gun.maxAmmoBundles()));
        menu.setItem(30, item(Material.HOPPER, ChatColor.YELLOW + "Drop Replacement Gun Here",
                List.of(ChatColor.GRAY + "Copies the cursor item without consuming it.")));
        menu.setItem(32, item(Material.LAVA_BUCKET, ChatColor.RED + "Delete Gun", List.of(ChatColor.GRAY + "Also removes its ammo link.")));
        standardEditorFooter(menu);
        player.openInventory(menu);
    }

    private void openGrenadeEditor(Player player, UUID id, int page) {
        Optional<WeaponLootCatalog.GrenadeEntry> found = grenade(id);
        if (found.isEmpty()) { stale(player, false, page); return; }
        var grenade = found.get();
        Inventory menu = create(WeaponMenuHolder.target(WeaponMenuHolder.MenuType.GRENADE_EDITOR, id, page), 54,
                ChatColor.DARK_GRAY + "Edit Grenade");
        fillBorder(menu);
        menu.setItem(13, display(grenade.item(), List.of(ChatColor.GRAY + "Exact saved grenade preview.")));
        menu.setItem(20, item(Material.NETHER_STAR, rarityColor(grenade.rarity()) + "Rarity: " + grenade.rarity().name(), adjustLore()));
        menu.setItem(24, numberItem("Minimum Quantity", grenade.minQuantity()));
        menu.setItem(26, numberItem("Maximum Quantity", grenade.maxQuantity()));
        menu.setItem(30, item(Material.HOPPER, ChatColor.YELLOW + "Drop Replacement Grenade Here",
                List.of(ChatColor.GRAY + "Copies the cursor item without consuming it.")));
        menu.setItem(32, item(Material.LAVA_BUCKET, ChatColor.RED + "Delete Grenade", List.of()));
        standardEditorFooter(menu);
        player.openInventory(menu);
    }

    public void handleClick(Player player, WeaponMenuHolder holder, int slot, ClickType click) {
        try {
            switch (holder.type()) {
                case WIZARD -> wizardClick(player, slot, click);
                case GUNS -> catalogClick(player, holder, slot, true);
                case GRENADES -> catalogClick(player, holder, slot, false);
                case GUN_EDITOR -> gunEditorClick(player, holder, slot, click);
                case GRENADE_EDITOR -> grenadeEditorClick(player, holder, slot, click);
                case CONFIRM_GENERATE, CONFIRM_FILL, CONFIRM_CLEAR, CONFIRM_REMOVE,
                     CONFIRM_DELETE_GUN, CONFIRM_DELETE_GRENADE ->
                        confirmationClick(player, holder, slot);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            openWizard(player);
        }
    }

    public boolean handleDrop(Player player, WeaponMenuHolder holder, int slot, ItemStack dropped) {
        try {
            if (!acceptsDrop(holder.type(), slot)) return false;
            ItemStack copy = dropped(dropped);
            switch (holder.type()) {
                case GUNS -> {
                    if (slot != CLOSE) return false;
                    repository.addGun(copy);
                    openGuns(player, holder.page());
                    return true;
                }
                case GRENADES -> {
                    if (slot != CLOSE) return false;
                    repository.addGrenade(copy);
                    openGrenades(player, holder.page());
                    return true;
                }
                case GUN_EDITOR -> {
                    if (slot == 22) repository.setGunAmmo(holder.target(), copy);
                    else if (slot == 30) repository.replaceGun(holder.target(), copy);
                    else return false;
                    openGunEditor(player, holder.target(), holder.page());
                    return true;
                }
                case GRENADE_EDITOR -> {
                    if (slot != 30) return false;
                    repository.replaceGrenade(holder.target(), copy);
                    openGrenadeEditor(player, holder.target(), holder.page());
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            openWizard(player);
            return true;
        }
    }

    private static boolean acceptsDrop(WeaponMenuHolder.MenuType type, int slot) {
        return switch (type) {
            case GUNS, GRENADES -> slot == CLOSE;
            case GUN_EDITOR -> slot == 22 || slot == 30;
            case GRENADE_EDITOR -> slot == 30;
            default -> false;
        };
    }

    private void wizardClick(Player player, int slot, ClickType click) {
        WeaponLootCatalog catalog = repository.snapshot();
        switch (slot) {
            case 10 -> { selectionListener.giveWand(player); openWizard(player); }
            case 20 -> openGuns(player, 0);
            case 24 -> openGrenades(player, 0);
            case 30 -> { repository.setChances(adjust(catalog.settings().secondGunChance(), click, 0, 100),
                    catalog.settings().grenadeChance()); openWizard(player); }
            case 31 -> { repository.setMaxChunks(adjustChunks(catalog.settings().maxChunks(), click));
                openWizard(player); }
            case 32 -> { repository.setChances(catalog.settings().secondGunChance(),
                    adjust(catalog.settings().grenadeChance(), click, 0, 100)); openWizard(player); }
            case 33 -> { repository.setGeneratedChestCount(adjustGeneratedChestCount(
                    catalog.settings().generatedChestCount(), click)); openWizard(player); }
            case 37 -> openActionConfirmation(player, WeaponMenuHolder.MenuType.CONFIRM_GENERATE);
            case 39 -> openActionConfirmation(player, WeaponMenuHolder.MenuType.CONFIRM_FILL);
            case 41 -> openActionConfirmation(player, WeaponMenuHolder.MenuType.CONFIRM_CLEAR);
            case 43 -> openActionConfirmation(player, WeaponMenuHolder.MenuType.CONFIRM_REMOVE);
            case BACK -> openMain.accept(player);
            case CLOSE -> player.closeInventory();
            default -> { }
        }
    }

    private void catalogClick(Player player, WeaponMenuHolder holder, int slot, boolean guns) {
        if (slot == BACK) { openWizard(player); return; }
        if (slot == HELP) { player.closeInventory(); return; }
        if (slot == CLOSE) return;
        if (slot == 46 && holder.page() > 0) { if (guns) openGuns(player, holder.page() - 1); else openGrenades(player, holder.page() - 1); return; }
        if (slot == 52) { if (guns) openGuns(player, holder.page() + 1); else openGrenades(player, holder.page() + 1); return; }
        UUID target = holder.slotTarget(slot);
        if (target != null) { if (guns) openGunEditor(player, target, holder.page()); else openGrenadeEditor(player, target, holder.page()); }
    }

    private void gunEditorClick(Player player, WeaponMenuHolder holder, int slot, ClickType click) {
        var gun = gun(holder.target()).orElseThrow(() -> new IllegalArgumentException("That gun no longer exists."));
        if (slot == BACK) { openGuns(player, holder.page()); return; }
        if (slot == CLOSE) { player.closeInventory(); return; }
        switch (slot) {
            case 20 -> repository.updateGun(gun.id(), click.isLeftClick() ? gun.rarity().next() : gun.rarity().previous(), gun.minAmmoBundles(), gun.maxAmmoBundles());
            case 24 -> { int min = adjust(gun.minAmmoBundles(), click, 1, 64); repository.updateGun(gun.id(), gun.rarity(), min, Math.max(min, gun.maxAmmoBundles())); }
            case 26 -> repository.updateGun(gun.id(), gun.rarity(), gun.minAmmoBundles(), adjust(gun.maxAmmoBundles(), click, gun.minAmmoBundles(), 64));
            case 32 -> { openDeleteConfirmation(player, holder, true); return; }
            default -> { return; }
        }
        openGunEditor(player, gun.id(), holder.page());
    }

    private void grenadeEditorClick(Player player, WeaponMenuHolder holder, int slot, ClickType click) {
        var grenade = grenade(holder.target()).orElseThrow(() -> new IllegalArgumentException("That grenade no longer exists."));
        if (slot == BACK) { openGrenades(player, holder.page()); return; }
        if (slot == CLOSE) { player.closeInventory(); return; }
        switch (slot) {
            case 20 -> repository.updateGrenade(grenade.id(), click.isLeftClick() ? grenade.rarity().next() : grenade.rarity().previous(), grenade.minQuantity(), grenade.maxQuantity());
            case 24 -> { int min = adjust(grenade.minQuantity(), click, 1, 64); repository.updateGrenade(grenade.id(), grenade.rarity(), min, Math.max(min, grenade.maxQuantity())); }
            case 26 -> repository.updateGrenade(grenade.id(), grenade.rarity(), grenade.minQuantity(), adjust(grenade.maxQuantity(), click, grenade.minQuantity(), 64));
            case 32 -> { openDeleteConfirmation(player, holder, false); return; }
            default -> { return; }
        }
        openGrenadeEditor(player, grenade.id(), holder.page());
    }

    private void openActionConfirmation(Player player, WeaponMenuHolder.MenuType type) {
        List<String> errors;
        int chestCount;
        if (type == WeaponMenuHolder.MenuType.CONFIRM_GENERATE
                || type == WeaponMenuHolder.MenuType.CONFIRM_REMOVE) {
            GeneratedChestService.ActionType action = type == WeaponMenuHolder.MenuType.CONFIRM_GENERATE
                    ? GeneratedChestService.ActionType.GENERATE : GeneratedChestService.ActionType.REMOVE;
            GeneratedChestService.ActionPreview preview = generatedChestService.preview(action);
            errors = preview.errors();
            chestCount = type == WeaponMenuHolder.MenuType.CONFIRM_GENERATE
                    ? repository.snapshot().settings().generatedChestCount() : preview.registeredChests();
            if (!preview.success()) { sendErrors(player, errors); openWizard(player); return; }
        } else {
            WeaponChestService.ActionType action = type == WeaponMenuHolder.MenuType.CONFIRM_FILL
                    ? WeaponChestService.ActionType.FILL : WeaponChestService.ActionType.CLEAR;
            WeaponChestService.ActionPreview preview = chestService.preview(action);
            errors = preview.errors();
            chestCount = preview.chestCount();
            if (!preview.success()) { sendErrors(player, errors); openWizard(player); return; }
        }
        WeaponLootCatalog catalog = repository.snapshot();
        String expectedState = type == WeaponMenuHolder.MenuType.CONFIRM_GENERATE
                ? generationKey(catalog) : regionKey(catalog);
        Inventory menu = create(WeaponMenuHolder.confirmation(type, expectedState), 27,
                ChatColor.DARK_GRAY + "Confirm Chest Action");
        fillAll(menu, Material.GRAY_STAINED_GLASS_PANE);
        menu.setItem(11, item(Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm", List.of(
                ChatColor.GRAY + confirmationDescription(type))));
        menu.setItem(13, item(confirmationMaterial(type), ChatColor.YELLOW + confirmationName(type),
                confirmationRegionLore(catalog, chestCount)));
        menu.setItem(15, item(Material.RED_CONCRETE, ChatColor.RED + "Cancel", List.of()));
        player.openInventory(menu);
    }

    private void openDeleteConfirmation(Player player, WeaponMenuHolder holder, boolean gun) {
        WeaponMenuHolder.MenuType type = gun ? WeaponMenuHolder.MenuType.CONFIRM_DELETE_GUN
                : WeaponMenuHolder.MenuType.CONFIRM_DELETE_GRENADE;
        Inventory menu = create(WeaponMenuHolder.target(type, holder.target(), holder.page()), 27,
                ChatColor.DARK_GRAY + "Confirm Loot Deletion");
        fillAll(menu, Material.GRAY_STAINED_GLASS_PANE);
        menu.setItem(11, item(Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm Delete", List.of()));
        menu.setItem(15, item(Material.RED_CONCRETE, ChatColor.RED + "Cancel", List.of()));
        player.openInventory(menu);
    }

    private void confirmationClick(Player player, WeaponMenuHolder holder, int slot) {
        if (slot == 15) { if (holder.target() == null) openWizard(player); else if (holder.type() == WeaponMenuHolder.MenuType.CONFIRM_DELETE_GUN) openGunEditor(player, holder.target(), holder.page()); else openGrenadeEditor(player, holder.target(), holder.page()); return; }
        if (slot != 11) return;
        if ((holder.type() == WeaponMenuHolder.MenuType.CONFIRM_FILL
                || holder.type() == WeaponMenuHolder.MenuType.CONFIRM_CLEAR)
                && !Objects.equals(holder.expectedState(), regionKey(repository.snapshot()))) {
            player.sendMessage(ChatColor.YELLOW
                    + "The selected chest region changed while confirmation was open. Review it again.");
            openWizard(player);
            return;
        }
        if (holder.type() == WeaponMenuHolder.MenuType.CONFIRM_GENERATE
                && !Objects.equals(holder.expectedState(), generationKey(repository.snapshot()))) {
            player.sendMessage(ChatColor.YELLOW
                    + "The selected region or generated chest count changed while confirmation was open. Review it again.");
            openWizard(player);
            return;
        }
        switch (holder.type()) {
            case CONFIRM_GENERATE -> startGeneratedAction(
                    player, GeneratedChestService.ActionType.GENERATE, "generated");
            case CONFIRM_FILL -> startAction(player, WeaponChestService.ActionType.FILL, "filled");
            case CONFIRM_CLEAR -> startAction(player, WeaponChestService.ActionType.CLEAR, "emptied");
            case CONFIRM_REMOVE -> startGeneratedAction(
                    player, GeneratedChestService.ActionType.REMOVE, "removed");
            case CONFIRM_DELETE_GUN -> {
                if (repository.removeGun(holder.target())) player.sendMessage(ChatColor.GREEN + "Gun and its ammo link deleted.");
                else player.sendMessage(ChatColor.YELLOW + "That gun was already removed.");
                openGuns(player, holder.page());
            }
            case CONFIRM_DELETE_GRENADE -> {
                if (repository.removeGrenade(holder.target())) player.sendMessage(ChatColor.GREEN + "Grenade deleted.");
                else player.sendMessage(ChatColor.YELLOW + "That grenade was already removed.");
                openGrenades(player, holder.page());
            }
            default -> { }
        }
    }

    private void startAction(Player player, WeaponChestService.ActionType action, String verb) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW
                + "Scanning generated chunks in small batches. Missing terrain will not be generated...");
        ChestOperationProgressBar progressBar = startProgress(player);
        chestService.executeAsync(plugin, action, progressBar::update, result -> {
            finishProgress(player, progressBar, result.success(), result.affectedChests());
            reportAction(player, result, verb);
        });
    }

    private void startGeneratedAction(Player player, GeneratedChestService.ActionType action, String verb) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + (action == GeneratedChestService.ActionType.GENERATE
                ? "Finding safe outdoor chest sites in small batches..."
                : "Restoring registered generated chest sites in small batches..."));
        ChestOperationProgressBar progressBar = startProgress(player);
        generatedChestService.executeAsync(plugin, action, progressBar::update, result -> {
            finishProgress(player, progressBar, result.success(), result.affectedChests());
            if (result.success()) player.sendMessage(ChatColor.GREEN + "Successfully " + verb + " "
                    + result.affectedChests() + " chest structures.");
            else sendErrors(player, result.errors());
            openWizard(player);
        });
    }

    private ChestOperationProgressBar startProgress(Player player) {
        ChestOperationProgressBar progressBar = new ChestOperationProgressBar(plugin, player);
        ChestOperationProgressBar previous = progressBars.put(player.getUniqueId(), progressBar);
        if (previous != null) previous.close();
        return progressBar;
    }

    private void finishProgress(Player player, ChestOperationProgressBar progressBar,
                                boolean success, int affectedChests) {
        progressBars.remove(player.getUniqueId(), progressBar);
        progressBar.complete(success, affectedChests);
    }

    void closeProgress(Player player) {
        ChestOperationProgressBar progressBar = progressBars.remove(player.getUniqueId());
        if (progressBar != null) progressBar.close();
    }

    private void reportAction(Player player, WeaponChestService.ActionResult result, String verb) {
        if (result.success()) player.sendMessage(ChatColor.GREEN + "Successfully " + verb + " " + result.affectedChests() + " chest inventories.");
        else sendErrors(player, result.errors());
        openWizard(player);
    }

    private ItemStack dropped(ItemStack dropped) {
        if (dropped == null || dropped.getType() == Material.AIR || selectionListener.isWand(dropped)) {
            throw new IllegalArgumentException("Pick up the exact item and drop it onto the marked GUI input slot.");
        }
        return dropped.clone();
    }

    private Optional<WeaponLootCatalog.GunEntry> gun(UUID id) { return repository.snapshot().guns().stream().filter(entry -> entry.id().equals(id)).findFirst(); }
    private Optional<WeaponLootCatalog.GrenadeEntry> grenade(UUID id) { return repository.snapshot().grenades().stream().filter(entry -> entry.id().equals(id)).findFirst(); }
    private void stale(Player player, boolean guns, int page) { player.sendMessage(ChatColor.YELLOW + "That loot entry changed while the menu was open."); if (guns) openGuns(player, page); else openGrenades(player, page); }
    private static void sendErrors(Player player, List<String> errors) { errors.forEach(error -> player.sendMessage(ChatColor.RED + error)); }

    private static WeaponChestService.ActionPreview fillPreview(
            WeaponLootCatalog catalog, WeaponChestService.ActionPreview clearPreview) {
        List<String> errors = new ArrayList<>(clearPreview.errors());
        errors.addAll(catalog.errors());
        if (catalog.guns().isEmpty()) errors.add("Add at least one gun first.");
        if (catalog.guns().stream().anyMatch(gun -> gun.ammo() == null)) {
            errors.add("Every gun must have corresponding ammo.");
        }
        return new WeaponChestService.ActionPreview(errors.isEmpty(), clearPreview.chestCount(), errors);
    }

    private static List<String> confirmationRegionLore(WeaponLootCatalog catalog, int chestCount) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.RED + "Existing chest contents will be erased.");
        if (catalog.point1() != null && catalog.point2() != null) {
            ChestRegion region = ChestRegion.between(catalog.point1(), catalog.point2());
            lore.add("");
            lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + region.world());
            lore.add(ChatColor.GRAY + "Point 1: " + ChatColor.WHITE + catalog.point1().x() + ", "
                    + catalog.point1().y() + ", " + catalog.point1().z());
            lore.add(ChatColor.GRAY + "Point 2: " + ChatColor.WHITE + catalog.point2().x() + ", "
                    + catalog.point2().y() + ", " + catalog.point2().z());
            lore.add(ChatColor.GRAY + "Dimensions: " + ChatColor.WHITE
                    + dimension(region.minX(), region.maxX()) + " x "
                    + dimension(region.minY(), region.maxY()) + " x "
                    + dimension(region.minZ(), region.maxZ()));
            lore.add(ChatColor.GRAY + "Chunks: " + ChatColor.WHITE + region.chunkCount());
        }
        if (chestCount >= 0) {
            lore.add(ChatColor.GRAY + "Chest inventories: " + ChatColor.WHITE + chestCount);
        } else {
            lore.add(ChatColor.GRAY + "Chest inventories: " + ChatColor.WHITE + "discovered during scan");
        }
        return lore;
    }
    private static Material confirmationMaterial(WeaponMenuHolder.MenuType type) {
        return switch (type) {
            case CONFIRM_GENERATE -> Material.GOLD_BLOCK;
            case CONFIRM_FILL -> Material.CHEST;
            case CONFIRM_CLEAR -> Material.HOPPER;
            case CONFIRM_REMOVE -> Material.LAVA_BUCKET;
            default -> Material.PAPER;
        };
    }
    private static String confirmationName(WeaponMenuHolder.MenuType type) {
        return switch (type) {
            case CONFIRM_GENERATE -> "Regenerate Outdoor Layout";
            case CONFIRM_FILL -> "Fresh Loot Fill";
            case CONFIRM_CLEAR -> "Empty Eligible Inventories";
            case CONFIRM_REMOVE -> "Remove Plugin-Owned Layout";
            default -> "Chest Action";
        };
    }
    private static String confirmationDescription(WeaponMenuHolder.MenuType type) {
        return switch (type) {
            case CONFIRM_GENERATE -> "The previous plugin layout will be restored and replaced.";
            case CONFIRM_FILL, CONFIRM_CLEAR -> "Only tagged or exact 3x3-gold chests are eligible.";
            case CONFIRM_REMOVE -> "Only registry-owned generated structures will be restored.";
            default -> "Review this action before continuing.";
        };
    }
    private static String regionKey(WeaponLootCatalog catalog) {
        return String.valueOf(catalog.point1()) + '|' + catalog.point2();
    }

    private static String generationKey(WeaponLootCatalog catalog) {
        return regionKey(catalog) + "|generated=" + catalog.settings().generatedChestCount();
    }

    private Inventory create(WeaponMenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.bind(inventory);
        return inventory;
    }
    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(lore); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta); return item;
    }
    private static ItemStack display(ItemStack source, List<String> appended) {
        ItemStack item = source.clone(); ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
        if (!lore.isEmpty()) lore.add(""); lore.addAll(appended); meta.setLore(lore); item.setItemMeta(meta); return item;
    }
    private static ItemStack chanceItem(String name, int chance) { return item(Material.COMPARATOR, ChatColor.YELLOW + name + ": " + ChatColor.WHITE + chance + "%", adjustLore()); }
    private static ItemStack numberItem(String name, int value) { return item(Material.PAPER, ChatColor.YELLOW + name + ": " + ChatColor.WHITE + value, adjustLore()); }
    private static List<String> adjustLore() { return List.of(ChatColor.AQUA + "Left/Right: " + ChatColor.GRAY + "+/-", ChatColor.AQUA + "Shift: " + ChatColor.GRAY + "larger step"); }
    private static ItemStack actionItem(Material material, String name, WeaponChestService.ActionPreview preview) {
        List<String> lore = new ArrayList<>();
        if (preview.success()) {
            lore.add(preview.chestCount() >= 0
                    ? ChatColor.GREEN + "Ready: " + preview.chestCount() + " chest inventories."
                    : ChatColor.GREEN + "Ready: chests will be discovered during the scan.");
        }
        else preview.errors().forEach(error -> lore.add(ChatColor.RED + error));
        lore.add(""); lore.add(ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Review and confirm");
        return item(material, (preview.success() ? ChatColor.GREEN : ChatColor.RED) + name, lore);
    }
    private static ItemStack actionItem(Material material, String name, GeneratedChestService.ActionPreview preview) {
        List<String> lore = new ArrayList<>();
        if (preview.success()) lore.add(ChatColor.GREEN + "Ready. Registered layout: "
                + preview.registeredChests() + " chests.");
        else preview.errors().forEach(error -> lore.add(ChatColor.RED + error));
        lore.add(""); lore.add(ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Review and confirm");
        return item(material, (preview.success() ? ChatColor.GREEN : ChatColor.RED) + name, lore);
    }
    private static String pointLine(String label, BlockPoint point) { return point == null ? ChatColor.GRAY + label + ": " + ChatColor.RED + "Not set" : ChatColor.GRAY + label + ": " + ChatColor.WHITE + point.x() + ", " + point.y() + ", " + point.z() + " (" + point.world() + ")"; }
    private static long dimension(int min, int max) { return (long) max - min + 1L; }
    private static ChatColor rarityColor(LootRarity rarity) { return switch (rarity) { case COMMON -> ChatColor.WHITE; case UNCOMMON -> ChatColor.GREEN; case RARE -> ChatColor.BLUE; case EPIC -> ChatColor.DARK_PURPLE; case LEGENDARY -> ChatColor.GOLD; }; }
    private static int adjust(int value, ClickType click, int min, int max) { int step = click.isShiftClick() ? 25 : 5; if (max <= 64) step = click.isShiftClick() ? 5 : 1; return Math.max(min, Math.min(max, value + (click.isLeftClick() ? step : -step))); }
    private static int adjustChunks(int value, ClickType click) { int step = click.isShiftClick() ? 500 : 100; return Math.max(1, Math.min(WeaponLootCatalog.Settings.MAX_CONFIGURED_CHUNKS, value + (click.isLeftClick() ? step : -step))); }
    private static int adjustGeneratedChestCount(int value, ClickType click) { int step = click.isShiftClick() ? 50 : 10; return Math.max(1, Math.min(WeaponLootCatalog.Settings.MAX_GENERATED_CHESTS, value + (click.isLeftClick() ? step : -step))); }
    private static int clampPage(int requested, int size) { int pages = Math.max(1, (size + 35) / 36); return Math.max(0, Math.min(requested, pages - 1)); }
    private static <T> List<T> entriesForPage(List<T> list, int page) { int first = page * 36; return list.subList(first, Math.min(first + 36, list.size())); }
    private static Map<Integer, UUID> targets(List<UUID> ids, int page) { Map<Integer, UUID> map = new HashMap<>(); int first = page * 36; for (int i = first; i < Math.min(first + 36, ids.size()); i++) map.put(9 + i - first, ids.get(i)); return map; }
    private static void footer(Inventory menu, int page, int size, Material addMaterial, String addName) { menu.setItem(BACK, item(Material.ARROW, ChatColor.AQUA + "Back", List.of())); if (page > 0) menu.setItem(46, item(Material.ARROW, ChatColor.AQUA + "Previous", List.of())); menu.setItem(CLOSE, item(addMaterial, ChatColor.GREEN + addName, List.of(ChatColor.GRAY + "Pick an item up from your inventory,", ChatColor.GRAY + "then drop it here. The item is not consumed."))); if ((page + 1) * 36 < size) menu.setItem(52, item(Material.ARROW, ChatColor.AQUA + "Next", List.of())); menu.setItem(HELP, item(Material.BARRIER, ChatColor.RED + "Close", List.of())); }
    private static void standardEditorFooter(Inventory menu) { menu.setItem(BACK, item(Material.ARROW, ChatColor.AQUA + "Back", List.of())); menu.setItem(CLOSE, item(Material.BARRIER, ChatColor.RED + "Close", List.of())); }
    private static void fillBorder(Inventory menu) { for (int slot = 0; slot < menu.getSize(); slot++) if (slot < 9 || slot >= menu.getSize() - 9 || slot % 9 == 0 || slot % 9 == 8) menu.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", List.of())); }
    private static void fillTopBottom(Inventory menu) { for (int slot = 0; slot < 9; slot++) menu.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())); for (int slot = menu.getSize() - 9; slot < menu.getSize(); slot++) menu.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())); }
    private static void fillAll(Inventory menu, Material material) { for (int slot = 0; slot < menu.getSize(); slot++) menu.setItem(slot, item(material, " ", List.of())); }
}
