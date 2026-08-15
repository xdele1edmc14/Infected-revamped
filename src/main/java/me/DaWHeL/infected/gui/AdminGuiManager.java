package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AdminGuiManager implements AdminGuiNavigator, AdminGuiClickHandler {
    private final InfectedPlugin plugin;
    private final GameManager gameManager;
    private final AdminSetupService setupService;
    private final AdminEventActions eventActions;

    public AdminGuiManager(InfectedPlugin plugin, GameManager gameManager, AdminSetupService setupService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.setupService = Objects.requireNonNull(setupService, "setupService");
        this.eventActions = new AdminEventActions(plugin, gameManager, setupService);
    }

    @Override
    public void openMain(Player player) {
        AdminMenuHolder holder = AdminMenuHolder.root(AdminMenuHolder.MenuType.MAIN);
        Inventory menu = create(holder, AdminGuiLayout.MAIN_SIZE,
                ChatColor.DARK_GRAY + "Infected Event Control");
        fillBorder(menu);

        AdminSetupService.SetupSnapshot snapshot = snapshot();
        boolean running = gameManager.isGameRunning();

        menu.setItem(AdminGuiLayout.STATUS, AdminGuiItems.item(
                running ? Material.CLOCK : Material.COMPASS,
                running ? ChatColor.GREEN : ChatColor.YELLOW,
                "Event Status",
                ChatColor.GRAY + "State: " + (running ? ChatColor.GREEN + "Running" : ChatColor.YELLOW + "Stopped"),
                ChatColor.GRAY + "Survivors: " + ChatColor.GREEN + snapshot.survivors(),
                ChatColor.GRAY + "Infected: " + ChatColor.RED + snapshot.infected(),
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Open live status"
        ));
        menu.setItem(AdminGuiLayout.INFECTED_SPAWN, infectedSpawnItem());
        menu.setItem(AdminGuiLayout.TELEPORT_POINTS, AdminGuiItems.item(
                Material.ENDER_PEARL,
                snapshot.teleportPointCount() > 0 ? ChatColor.AQUA : ChatColor.RED,
                "Teleport Points",
                ChatColor.GRAY + "Configured: " + (snapshot.teleportPointCount() > 0
                        ? ChatColor.GREEN + String.valueOf(snapshot.teleportPointCount())
                        : ChatColor.RED + "None"),
                ChatColor.GRAY + "Existing shared event locations.",
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Manage points"
        ));
        menu.setItem(AdminGuiLayout.SETUP_STATUS, AdminGuiItems.item(
                Material.WRITABLE_BOOK,
                snapshot.ready() ? ChatColor.GREEN : ChatColor.YELLOW,
                "Setup Status",
                snapshot.ready()
                        ? ChatColor.GREEN + "Setup appears ready."
                        : ChatColor.YELLOW + "Setup needs attention.",
                ChatColor.GRAY + "Review the current checklist.",
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Open checklist"
        ));
        menu.setItem(AdminGuiLayout.EVENT_PLAYERS, AdminGuiItems.item(
                Material.PLAYER_HEAD,
                ChatColor.AQUA,
                "Event Players",
                ChatColor.GRAY + "Survivors: " + ChatColor.GREEN + snapshot.survivors(),
                ChatColor.GRAY + "Infected: " + ChatColor.RED + snapshot.infected(),
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "View players"
        ));

        boolean canStart = AdminGuiPolicy.canStart(snapshot, running);
        menu.setItem(AdminGuiLayout.START_EVENT, AdminGuiItems.item(
                canStart ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                canStart ? ChatColor.GREEN : ChatColor.RED,
                "Start Event",
                canStart ? ChatColor.GREEN + "Ready to start." : startBlockedReason(snapshot, running),
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Review and confirm"
        ));
        menu.setItem(AdminGuiLayout.STOP_EVENT, AdminGuiItems.item(
                running ? Material.RED_CONCRETE : Material.BARRIER,
                ChatColor.RED,
                "Stop Event",
                running ? ChatColor.RED + "Stop the current event." : ChatColor.GRAY + "No event is running.",
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Review and confirm"
        ));
        menu.setItem(AdminGuiLayout.RELOAD_CONFIG, AdminGuiItems.item(
                Material.COMPARATOR,
                ChatColor.YELLOW,
                "Reload Config",
                ChatColor.GRAY + "Reload configuration from disk.",
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Reload"
        ));
        menu.setItem(AdminGuiLayout.QUICK_HELP, AdminGuiItems.item(
                Material.BOOK,
                ChatColor.AQUA,
                "Quick Help",
                ChatColor.GRAY + "View the four-step setup guide.",
                ChatColor.GRAY + "This is an event control desk,",
                ChatColor.GRAY + "not a gameplay configuration editor.",
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Open guide"
        ));
        menu.setItem(AdminGuiLayout.MAIN_BACK, AdminGuiItems.item(
                Material.GRAY_DYE, ChatColor.DARK_GRAY, "Back",
                ChatColor.GRAY + "You are at the main control desk."
        ));
        menu.setItem(AdminGuiLayout.MAIN_CLOSE, closeItem());
        player.openInventory(menu);
    }

    public void openLiveStatus(Player player) {
        AdminMenuHolder holder = AdminMenuHolder.root(AdminMenuHolder.MenuType.LIVE_STATUS);
        Inventory menu = create(holder, 54, ChatColor.DARK_GRAY + "Infected Live Status");
        fillBorder(menu);
        AdminSetupService.SetupSnapshot snapshot = snapshot();
        boolean running = gameManager.isGameRunning();
        menu.setItem(4, AdminGuiItems.item(
                Material.NETHER_STAR,
                running ? ChatColor.GREEN : ChatColor.YELLOW,
                "Live Event Status",
                ChatColor.GRAY + "State: " + (running ? ChatColor.GREEN + "Running" : ChatColor.YELLOW + "Stopped"),
                ChatColor.GRAY + "Setup: " + (snapshot.ready() ? ChatColor.GREEN + "Ready" : ChatColor.RED + "Incomplete")
        ));
        menu.setItem(20, AdminGuiItems.item(Material.LIME_DYE, ChatColor.GREEN, "Survivors",
                ChatColor.GRAY + "Current total: " + ChatColor.WHITE + snapshot.survivors()));
        menu.setItem(24, AdminGuiItems.item(Material.RED_DYE, ChatColor.RED, "Infected",
                ChatColor.GRAY + "Current total: " + ChatColor.WHITE + snapshot.infected()));
        addStandardFooter(menu, true, true);
        player.openInventory(menu);
    }

    @Override
    public void openTeleportPoints(Player player, int requestedPage) {
        List<AdminSetupService.TeleportPoint> points = setupService.teleportPoints();
        int page = AdminGuiPolicy.clampPage(requestedPage, points.size(), AdminGuiLayout.PAGE_SIZE);
        int pages = AdminGuiPolicy.pageCount(points.size(), AdminGuiLayout.PAGE_SIZE);
        int first = page * AdminGuiLayout.PAGE_SIZE;
        int last = Math.min(first + AdminGuiLayout.PAGE_SIZE, points.size());
        Map<Integer, String> slotTargets = new HashMap<>();
        for (int index = first; index < last; index++) {
            slotTargets.put(AdminGuiLayout.contentSlot(index - first), points.get(index).name());
        }
        AdminMenuHolder holder = AdminMenuHolder.page(
                AdminMenuHolder.MenuType.TELEPORT_POINTS, page, slotTargets);
        Inventory menu = create(holder, 54, ChatColor.DARK_GRAY + "Teleport Points");
        fillTopAndBottom(menu);

        menu.setItem(4, AdminGuiItems.item(
                Material.ENDER_EYE,
                ChatColor.AQUA,
                "Teleport Points",
                ChatColor.GRAY + "Existing shared event locations: " + ChatColor.WHITE + points.size(),
                ChatColor.GRAY + "Page " + (page + 1) + " of " + pages,
                ChatColor.YELLOW + "GUI changes never modify arena blocks."
        ));

        for (int index = first; index < last; index++) {
            AdminSetupService.TeleportPoint point = points.get(index);
            AdminSetupService.StoredLocation location = point.location();
            menu.setItem(AdminGuiLayout.contentSlot(index - first), AdminGuiItems.item(
                    Material.ENDER_PEARL,
                    ChatColor.AQUA,
                    point.name(),
                    ChatColor.GRAY + "World: " + ChatColor.WHITE + location.world(),
                    ChatColor.GRAY + "X: " + coordinate(location.x())
                            + "  Y: " + coordinate(location.y())
                            + "  Z: " + coordinate(location.z()),
                    "",
                    ChatColor.AQUA + "Left-click: " + ChatColor.GRAY + "Teleport",
                    ChatColor.RED + "Shift-right-click: " + ChatColor.GRAY + "Delete"
            ));
        }

        menu.setItem(AdminGuiLayout.BACK, backItem());
        if (page > 0) {
            menu.setItem(AdminGuiLayout.PREVIOUS_PAGE, AdminGuiItems.item(
                    Material.ARROW, ChatColor.AQUA, "Previous Page", ChatColor.GRAY + "Go to page " + page));
        }
        menu.setItem(AdminGuiLayout.CLOSE, AdminGuiItems.item(
                Material.LIME_DYE,
                ChatColor.GREEN,
                "Add Current Location",
                ChatColor.GRAY + "Name the new shared teleport point",
                ChatColor.GRAY + "with a simple command.",
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Show command"
        ));
        if (page + 1 < pages) {
            menu.setItem(AdminGuiLayout.NEXT_PAGE, AdminGuiItems.item(
                    Material.ARROW, ChatColor.AQUA, "Next Page", ChatColor.GRAY + "Go to page " + (page + 2)));
        }
        menu.setItem(AdminGuiLayout.HELP, closeItem());
        player.openInventory(menu);
    }

    public void openSetupStatus(Player player) {
        AdminMenuHolder holder = AdminMenuHolder.root(AdminMenuHolder.MenuType.SETUP_STATUS);
        Inventory menu = create(holder, 54, ChatColor.DARK_GRAY + "Infected Setup Status");
        fillBorder(menu);
        AdminSetupService.SetupSnapshot snapshot = snapshot();

        menu.setItem(4, AdminGuiItems.item(Material.WRITABLE_BOOK, ChatColor.AQUA, "Setup Checklist",
                ChatColor.GRAY + "A read-only view of existing configuration."));
        menu.setItem(10, checklistItem("Infected Spawn", snapshot.infectedSpawnConfigured(),
                snapshot.infectedSpawnConfigured() ? "Configured" : "Missing"));
        menu.setItem(12, checklistItem("Teleport Points", snapshot.teleportPointCount() > 0,
                snapshot.teleportPointCount() + " configured"));
        boolean participants = snapshot.survivors() + snapshot.infected() > 0;
        menu.setItem(14, AdminGuiItems.item(
                Material.PLAYER_HEAD,
                participants ? ChatColor.AQUA : ChatColor.YELLOW,
                "Current Participants",
                ChatColor.GRAY + "Survivors: " + ChatColor.GREEN + snapshot.survivors(),
                ChatColor.GRAY + "Infected: " + ChatColor.RED + snapshot.infected(),
                participants ? ChatColor.GRAY + "Players are registered." : ChatColor.YELLOW + "No players are registered."
        ));
        menu.setItem(28, configItem("Starting Infected", snapshot.startingInfected(), "players"));
        menu.setItem(30, configItem("Infected Teleport Delay", snapshot.infectedTeleportDelay(), "seconds"));
        menu.setItem(32, configItem("Teleport Batch Size", snapshot.teleportBatchSize(), "players"));

        List<String> readinessLore = new ArrayList<>();
        if (snapshot.ready()) {
            readinessLore.add(ChatColor.GREEN + "Infected spawn configured.");
            readinessLore.add(ChatColor.GREEN + "At least one teleport point configured.");
        } else {
            if (!snapshot.infectedSpawnConfigured()) {
                readinessLore.add(ChatColor.RED + "Missing: Infected spawn");
            }
            if (snapshot.teleportPointCount() == 0) {
                readinessLore.add(ChatColor.RED + "Missing: Teleport points");
            }
        }
        readinessLore.add("");
        readinessLore.add(ChatColor.GRAY + "This checklist does not rewrite start validation.");
        menu.setItem(40, AdminGuiItems.item(
                snapshot.ready() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                (snapshot.ready() ? ChatColor.GREEN : ChatColor.RED)
                        + (snapshot.ready() ? "Setup Appears Ready" : "Setup Incomplete"),
                readinessLore
        ));
        addStandardFooter(menu, true, true);
        player.openInventory(menu);
    }

    public void openPlayers(Player player, int requestedPage) {
        List<PlayerEntry> players = playerEntries();
        int page = AdminGuiPolicy.clampPage(requestedPage, players.size(), AdminGuiLayout.PAGE_SIZE);
        int pages = AdminGuiPolicy.pageCount(players.size(), AdminGuiLayout.PAGE_SIZE);
        int first = page * AdminGuiLayout.PAGE_SIZE;
        int last = Math.min(first + AdminGuiLayout.PAGE_SIZE, players.size());
        Map<Integer, String> slotTargets = new HashMap<>();
        for (int index = first; index < last; index++) {
            slotTargets.put(AdminGuiLayout.contentSlot(index - first), players.get(index).id().toString());
        }
        AdminMenuHolder holder = AdminMenuHolder.page(AdminMenuHolder.MenuType.PLAYERS, page, slotTargets);
        Inventory menu = create(holder, 54, ChatColor.DARK_GRAY + "Infected Event Players");
        fillTopAndBottom(menu);
        menu.setItem(4, AdminGuiItems.item(
                Material.PLAYER_HEAD,
                ChatColor.AQUA,
                "Event Players",
                ChatColor.GRAY + "Survivors: " + ChatColor.GREEN + gameManager.getSurvivors().size(),
                ChatColor.GRAY + "Infected: " + ChatColor.RED + gameManager.getInfected().size(),
                ChatColor.GRAY + "Page " + (page + 1) + " of " + pages
        ));

        for (int index = first; index < last; index++) {
            PlayerEntry entry = players.get(index);
            menu.setItem(AdminGuiLayout.contentSlot(index - first), AdminGuiItems.playerHead(
                    entry.player(), entry.infected() ? ChatColor.RED : ChatColor.GREEN,
                    entry.infected() ? "Infected" : "Survivor"));
        }
        menu.setItem(AdminGuiLayout.BACK, backItem());
        if (page > 0) {
            menu.setItem(AdminGuiLayout.PREVIOUS_PAGE,
                    AdminGuiItems.item(Material.ARROW, ChatColor.AQUA, "Previous Page", ChatColor.GRAY + "Go back"));
        }
        menu.setItem(AdminGuiLayout.CLOSE, closeItem());
        if (page + 1 < pages) {
            menu.setItem(AdminGuiLayout.NEXT_PAGE,
                    AdminGuiItems.item(Material.ARROW, ChatColor.AQUA, "Next Page", ChatColor.GRAY + "Continue"));
        }
        menu.setItem(AdminGuiLayout.HELP, helpItem());
        player.openInventory(menu);
    }

    public void openPlayerActions(Player administrator, UUID targetId, int returnPage) {
        Optional<PlayerEntry> entry = playerEntries().stream()
                .filter(candidate -> candidate.id().equals(targetId))
                .findFirst();
        if (entry.isEmpty()) {
            administrator.sendMessage(ChatColor.RED + "That participant is no longer available.");
            openPlayers(administrator, returnPage);
            return;
        }

        PlayerEntry target = entry.get();
        AdminMenuHolder holder = AdminMenuHolder.playerActions(target.id(), returnPage, teamKey(target));
        Inventory menu = create(holder, 54, ChatColor.DARK_GRAY + "Player Actions");
        fillBorder(menu);
        menu.setItem(4, AdminGuiItems.playerHead(target.player(),
                target.infected() ? ChatColor.RED : ChatColor.GREEN,
                target.infected() ? "Infected" : "Survivor"));
        menu.setItem(21, AdminGuiItems.item(
                Material.ROTTEN_FLESH,
                ChatColor.YELLOW,
                "Toggle Survivor / Infected",
                ChatColor.GRAY + "Uses the existing team-toggle action.",
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "Toggle team"
        ));
        menu.setItem(23, AdminGuiItems.item(
                Material.BARRIER,
                ChatColor.RED,
                "Remove Player — Unavailable",
                ChatColor.YELLOW + "The existing remove action is not safe:",
                ChatColor.GRAY + "it resets and re-adds survivors.",
                ChatColor.GRAY + "Use the legacy command only if you accept that behavior."
        ));
        addStandardFooter(menu, true, false);
        playerOpen(administrator, menu);
    }

    public void openHelp(Player player) {
        AdminMenuHolder holder = AdminMenuHolder.root(AdminMenuHolder.MenuType.HELP);
        Inventory menu = create(holder, 54, ChatColor.DARK_GRAY + "Infected Control Help");
        fillBorder(menu);
        menu.setItem(4, AdminGuiItems.item(Material.BOOK, ChatColor.AQUA, "Quick Setup Guide",
                ChatColor.GRAY + "Use the control desk in this order:"));
        menu.setItem(19, guideItem(1, "Configure Infected Spawn", "Left-click the spawn control at your location."));
        menu.setItem(21, guideItem(2, "Add Teleport Points", "Use the point menu's config-only naming command."));
        menu.setItem(23, guideItem(3, "Check Setup Status", "Review the checklist and exact warnings."));
        menu.setItem(25, guideItem(4, "Start the Event", "Review the confirmation before starting."));
        menu.setItem(31, AdminGuiItems.item(
                Material.LECTERN,
                ChatColor.YELLOW,
                "Control Desk Only",
                ChatColor.GRAY + "This GUI controls setup and event actions.",
                ChatColor.GRAY + "It does not edit gameplay rules or balancing."
        ));
        addStandardFooter(menu, true, false);
        player.openInventory(menu);
    }

    @Override
    public void handleClick(Player player, AdminMenuHolder holder, int rawSlot, ClickType clickType) {
        switch (holder.type()) {
            case MAIN -> handleMainClick(player, rawSlot, clickType);
            case LIVE_STATUS, SETUP_STATUS, HELP -> handleInformationalClick(player, rawSlot);
            case TELEPORT_POINTS -> handleTeleportClick(player, holder, rawSlot, clickType);
            case PLAYERS -> handlePlayersClick(player, holder, rawSlot);
            case PLAYER_ACTIONS -> handlePlayerActionClick(player, holder, rawSlot);
            case CONFIRMATION -> handleConfirmationClick(player, holder, rawSlot);
        }
    }

    private void handleMainClick(Player player, int slot, ClickType click) {
        switch (slot) {
            case AdminGuiLayout.STATUS -> openLiveStatus(player);
            case AdminGuiLayout.INFECTED_SPAWN -> handleSpawnClick(player, click);
            case AdminGuiLayout.TELEPORT_POINTS -> openTeleportPoints(player, 0);
            case AdminGuiLayout.SETUP_STATUS -> openSetupStatus(player);
            case AdminGuiLayout.EVENT_PLAYERS -> openPlayers(player, 0);
            case AdminGuiLayout.START_EVENT -> requestStart(player);
            case AdminGuiLayout.STOP_EVENT -> requestStop(player);
            case AdminGuiLayout.RELOAD_CONFIG -> reload(player);
            case AdminGuiLayout.QUICK_HELP -> openHelp(player);
            case AdminGuiLayout.MAIN_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleSpawnClick(Player player, ClickType click) {
        if (click.isRightClick() && click.isShiftClick()) {
            if (setupService.infectedSpawn().isEmpty()) {
                player.sendMessage(ChatColor.RED + "No infected spawn is configured.");
                openMain(player);
                return;
            }
            AdminSetupService.StoredLocation expected = setupService.infectedSpawn().orElseThrow();
            openConfirmation(player, AdminMenuHolder.ConfirmationAction.CLEAR_INFECTED_SPAWN,
                    null, stateKey(expected), 0);
            return;
        }
        if (click.isRightClick()) {
            teleportToSpawn(player);
            return;
        }
        if (click.isLeftClick()) {
            setupService.setInfectedSpawn(player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Infected spawn saved at your current location.");
            openMain(player);
        }
    }

    private void handleInformationalClick(Player player, int slot) {
        if (slot == AdminGuiLayout.BACK) {
            openMain(player);
        } else if (slot == AdminGuiLayout.CLOSE) {
            player.closeInventory();
        } else if (slot == AdminGuiLayout.HELP) {
            openHelp(player);
        }
    }

    private void handleTeleportClick(Player player, AdminMenuHolder holder, int slot, ClickType click) {
        int page = holder.page();
        if (slot == AdminGuiLayout.BACK) {
            openMain(player);
            return;
        }
        if (slot == AdminGuiLayout.PREVIOUS_PAGE) {
            openTeleportPoints(player, page - 1);
            return;
        }
        if (slot == AdminGuiLayout.CLOSE) {
            player.closeInventory();
            player.sendMessage(ChatColor.AQUA + "Type " + ChatColor.YELLOW
                    + "/infected gui addteleport <name>" + ChatColor.AQUA
                    + " to save your current location without changing blocks.");
            return;
        }
        if (slot == AdminGuiLayout.NEXT_PAGE) {
            openTeleportPoints(player, page + 1);
            return;
        }
        if (slot == AdminGuiLayout.HELP) {
            player.closeInventory();
            return;
        }

        String pointName = holder.slotTarget(slot);
        if (pointName == null) {
            return;
        }
        Optional<AdminSetupService.TeleportPoint> selected = setupService.teleportPoints().stream()
                .filter(point -> point.name().equals(pointName))
                .findFirst();
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "That teleport point changed while the menu was open.");
            openTeleportPoints(player, page);
            return;
        }
        AdminSetupService.TeleportPoint point = selected.get();
        if (click.isRightClick() && click.isShiftClick()) {
            openConfirmation(player, AdminMenuHolder.ConfirmationAction.DELETE_TELEPORT_POINT,
                    point.name(), stateKey(point.location()), page);
        } else if (click.isLeftClick()) {
            teleportToPoint(player, point, page);
        }
    }

    private void handlePlayersClick(Player player, AdminMenuHolder holder, int slot) {
        int page = holder.page();
        if (slot == AdminGuiLayout.BACK) {
            openMain(player);
            return;
        }
        if (slot == AdminGuiLayout.CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == AdminGuiLayout.PREVIOUS_PAGE) {
            openPlayers(player, page - 1);
            return;
        }
        if (slot == AdminGuiLayout.NEXT_PAGE) {
            openPlayers(player, page + 1);
            return;
        }
        if (slot == AdminGuiLayout.HELP) {
            openHelp(player);
            return;
        }

        String target = holder.slotTarget(slot);
        if (target == null) {
            return;
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(target);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.YELLOW + "That player changed while the menu was open.");
            openPlayers(player, page);
            return;
        }
        Optional<PlayerEntry> selected = playerEntries().stream()
                .filter(entry -> entry.id().equals(targetId))
                .findFirst();
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "That player changed while the menu was open.");
            openPlayers(player, page);
            return;
        }
        openPlayerActions(player, selected.get().id(), page);
    }

    private void handlePlayerActionClick(Player administrator, AdminMenuHolder holder, int slot) {
        if (slot == AdminGuiLayout.BACK) {
            openPlayers(administrator, holder.page());
            return;
        }
        if (slot == AdminGuiLayout.CLOSE) {
            administrator.closeInventory();
            return;
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(holder.target());
        } catch (IllegalArgumentException exception) {
            administrator.sendMessage(ChatColor.RED + "That player selection is no longer valid.");
            openPlayers(administrator, holder.page());
            return;
        }
        if (slot == 21) {
            Optional<PlayerEntry> currentEntry = playerEntries().stream()
                    .filter(entry -> entry.id().equals(targetId))
                    .findFirst();
            if (currentEntry.isEmpty()) {
                administrator.sendMessage(ChatColor.RED + "That participant is no longer online or registered.");
                openPlayers(administrator, holder.page());
                return;
            }
            if (!AdminGuiPolicy.matchesExpected(holder.expectedState(), teamKey(currentEntry.get()))) {
                administrator.sendMessage(ChatColor.YELLOW
                        + "That participant's team changed while the menu was open. Review the new state first.");
                openPlayerActions(administrator, targetId, holder.page());
                return;
            }
            Player target = currentEntry.get().player();
            gameManager.toggleZombie(target);
            administrator.sendMessage(ChatColor.GREEN + "Toggled the team for " + target.getName() + ".");
            openPlayerActions(administrator, targetId, holder.page());
        } else if (slot == 23) {
            administrator.sendMessage(ChatColor.YELLOW
                    + "Remove Player is unavailable here because the existing action is unsafe.");
        }
    }

    private void requestStart(Player player) {
        if (!AdminGuiPolicy.canStart(snapshot(), gameManager.isGameRunning())) {
            player.sendMessage(ChatColor.RED + ChatColor.stripColor(startBlockedReason(snapshot(), gameManager.isGameRunning())));
            openMain(player);
            return;
        }
        openConfirmation(player, AdminMenuHolder.ConfirmationAction.START, null, null, 0);
    }

    private void requestStop(Player player) {
        if (!gameManager.isGameRunning()) {
            player.sendMessage(ChatColor.YELLOW + "No Infected event is currently running.");
            openMain(player);
            return;
        }
        openConfirmation(player, AdminMenuHolder.ConfirmationAction.STOP, null, null, 0);
    }

    private void reload(Player player) {
        plugin.reloadConfig();
        String message = plugin.getConfig().getString("messages.config-reloaded",
                "&aInfected plugin configuration reloaded!");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        openMain(player);
    }

    private void openConfirmation(
            Player player,
            AdminMenuHolder.ConfirmationAction action,
            String target,
            String expectedState,
            int returnPage
    ) {
        AdminMenuHolder holder = AdminMenuHolder.confirmation(action, target, expectedState, returnPage);
        Inventory menu = create(holder, 27, ChatColor.DARK_GRAY + confirmationTitle(action));
        fillAll(menu, Material.GRAY_STAINED_GLASS_PANE);
        menu.setItem(AdminGuiLayout.CONFIRM, AdminGuiItems.item(
                Material.LIME_CONCRETE,
                ChatColor.GREEN,
                confirmLabel(action),
                ChatColor.GRAY + "This action will run immediately."
        ));
        menu.setItem(AdminGuiLayout.CONFIRMATION_SUMMARY, confirmationSummary(action, target));
        menu.setItem(AdminGuiLayout.CANCEL, AdminGuiItems.item(
                Material.RED_CONCRETE,
                ChatColor.RED,
                action == AdminMenuHolder.ConfirmationAction.STOP ? "Go Back" : "Cancel",
                ChatColor.GRAY + "Return without making changes."
        ));
        player.openInventory(menu);
    }

    private void handleConfirmationClick(Player player, AdminMenuHolder holder, int slot) {
        if (slot == AdminGuiLayout.CANCEL) {
            returnFromConfirmation(player, holder);
            return;
        }
        if (slot != AdminGuiLayout.CONFIRM) {
            return;
        }

        switch (holder.confirmationAction()) {
            case START -> {
                AdminEventActions.ActionResult result = eventActions.start(player);
                player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
                if (result.success()) {
                    player.closeInventory();
                } else {
                    openMain(player);
                }
            }
            case STOP -> {
                AdminEventActions.ActionResult result = eventActions.stop();
                player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.YELLOW) + result.message());
                player.closeInventory();
            }
            case CLEAR_INFECTED_SPAWN -> {
                String currentState = setupService.infectedSpawn().map(AdminGuiManager::stateKey).orElse(null);
                if (!AdminGuiPolicy.matchesExpected(holder.expectedState(), currentState)) {
                    player.sendMessage(ChatColor.YELLOW
                            + "The infected spawn changed while confirmation was open. Review it again.");
                    openMain(player);
                    return;
                }
                if (setupService.clearInfectedSpawn()) {
                    player.sendMessage(ChatColor.GREEN + "Cleared the configured infected spawn.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "The infected spawn was already missing.");
                }
                openMain(player);
            }
            case DELETE_TELEPORT_POINT -> {
                String target = holder.target();
                String currentState = target == null ? null : setupService.teleportPoints().stream()
                        .filter(point -> point.name().equals(target))
                        .map(point -> stateKey(point.location()))
                        .findFirst()
                        .orElse(null);
                if (!AdminGuiPolicy.matchesExpected(holder.expectedState(), currentState)) {
                    player.sendMessage(ChatColor.YELLOW
                            + "That teleport point changed while confirmation was open. Review it again.");
                    openTeleportPoints(player, holder.page());
                    return;
                }
                if (setupService.deleteTeleportPoint(target)) {
                    player.sendMessage(ChatColor.GREEN + "Deleted teleport point '" + target
                            + "'. World blocks were left unchanged.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "That teleport point was already missing.");
                }
                openTeleportPoints(player, holder.page());
            }
        }
    }

    private void returnFromConfirmation(Player player, AdminMenuHolder holder) {
        if (holder.confirmationAction() == AdminMenuHolder.ConfirmationAction.DELETE_TELEPORT_POINT) {
            openTeleportPoints(player, holder.page());
        } else {
            openMain(player);
        }
    }

    private void teleportToSpawn(Player player) {
        Optional<AdminSetupService.StoredLocation> stored = setupService.infectedSpawn();
        if (stored.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No infected spawn is configured.");
            openMain(player);
            return;
        }
        Location location = resolve(stored.get(), false);
        if (location == null) {
            player.sendMessage(ChatColor.RED + "The infected spawn world is not loaded.");
            openMain(player);
            return;
        }
        if (player.teleport(location)) {
            player.sendMessage(ChatColor.GREEN + "Teleported to the infected spawn.");
        } else {
            player.sendMessage(ChatColor.RED + "The teleport was cancelled by another plugin.");
            openMain(player);
        }
    }

    private void teleportToPoint(Player player, AdminSetupService.TeleportPoint point, int returnPage) {
        Location location = resolve(point.location(), true);
        if (location == null) {
            player.sendMessage(ChatColor.RED + "World '" + point.location().world() + "' is not loaded.");
            openTeleportPoints(player, returnPage);
            return;
        }
        if (player.teleport(location)) {
            player.sendMessage(ChatColor.GREEN + "Teleported to '" + point.name() + "'.");
        } else {
            player.sendMessage(ChatColor.RED + "The teleport was cancelled by another plugin.");
            openTeleportPoints(player, returnPage);
        }
    }

    private static Location resolve(AdminSetupService.StoredLocation stored, boolean safePointOffset) {
        World world = Bukkit.getWorld(stored.world());
        if (world == null) {
            return null;
        }
        double x = stored.x();
        double y = stored.y();
        double z = stored.z();
        if (safePointOffset) {
            x = Math.floor(x) + 0.5;
            y = Math.floor(y) + 1.0;
            z = Math.floor(z) + 0.5;
        }
        return new Location(world, x, y, z, stored.yaw(), stored.pitch());
    }

    private Inventory create(AdminMenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.bind(inventory);
        return inventory;
    }

    private AdminSetupService.SetupSnapshot snapshot() {
        return setupService.snapshot(gameManager.getSurvivors().size(), gameManager.getInfected().size());
    }

    private List<PlayerEntry> playerEntries() {
        List<PlayerEntry> entries = new ArrayList<>();
        for (Survivor survivor : gameManager.getSurvivors()) {
            Player player = survivor.getPlayer();
            if (player != null && player.isOnline()) {
                entries.add(new PlayerEntry(player.getUniqueId(), player, false));
            }
        }
        for (Infected infected : gameManager.getInfected()) {
            Player player = infected.getPlayer();
            if (player != null && player.isOnline()) {
                entries.add(new PlayerEntry(player.getUniqueId(), player, true));
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.player().getName(), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private org.bukkit.inventory.ItemStack infectedSpawnItem() {
        Optional<AdminSetupService.StoredLocation> stored = setupService.infectedSpawn();
        if (stored.isEmpty()) {
            return AdminGuiItems.item(
                    Material.ZOMBIE_HEAD,
                    ChatColor.RED,
                    "Infected Spawn — Missing",
                    ChatColor.RED + "No infected spawn is configured.",
                    "",
                    ChatColor.AQUA + "Left-click: " + ChatColor.GRAY + "Set current location",
                    ChatColor.AQUA + "Right-click: " + ChatColor.GRAY + "Teleport",
                    ChatColor.RED + "Shift-right-click: " + ChatColor.GRAY + "Clear"
            );
        }
        AdminSetupService.StoredLocation location = stored.get();
        return AdminGuiItems.item(
                Material.ZOMBIE_HEAD,
                ChatColor.GREEN,
                "Infected Spawn — Configured",
                ChatColor.GRAY + "World: " + ChatColor.WHITE + location.world(),
                ChatColor.GRAY + "X: " + coordinate(location.x())
                        + "  Y: " + coordinate(location.y())
                        + "  Z: " + coordinate(location.z()),
                "",
                ChatColor.AQUA + "Left-click: " + ChatColor.GRAY + "Set current location",
                ChatColor.AQUA + "Right-click: " + ChatColor.GRAY + "Teleport",
                ChatColor.RED + "Shift-right-click: " + ChatColor.GRAY + "Clear"
        );
    }

    private static org.bukkit.inventory.ItemStack checklistItem(String name, boolean ready, String detail) {
        return AdminGuiItems.item(
                ready ? Material.LIME_DYE : Material.RED_DYE,
                ready ? ChatColor.GREEN : ChatColor.RED,
                name,
                (ready ? ChatColor.GREEN : ChatColor.RED) + detail
        );
    }

    private static org.bukkit.inventory.ItemStack configItem(String name, int value, String unit) {
        return AdminGuiItems.item(
                Material.PAPER,
                ChatColor.AQUA,
                name,
                ChatColor.GRAY + "Current value: " + ChatColor.WHITE + value + " " + unit
        );
    }

    private static org.bukkit.inventory.ItemStack guideItem(int step, String name, String description) {
        return AdminGuiItems.item(
                Material.MAP,
                ChatColor.AQUA,
                step + ". " + name,
                ChatColor.GRAY + description
        );
    }

    private static org.bukkit.inventory.ItemStack confirmationSummary(
            AdminMenuHolder.ConfirmationAction action,
            String target
    ) {
        return switch (action) {
            case START -> AdminGuiItems.item(Material.NETHER_STAR, ChatColor.YELLOW, "Start the Event?",
                    ChatColor.GRAY + "Runs the existing event start flow.");
            case STOP -> AdminGuiItems.item(Material.BARRIER, ChatColor.RED, "Stop the Current Event?",
                    ChatColor.GRAY + "Runs the existing event stop flow.");
            case CLEAR_INFECTED_SPAWN -> AdminGuiItems.item(Material.ZOMBIE_HEAD, ChatColor.RED,
                    "Clear Infected Spawn?", ChatColor.GRAY + "Removes only the saved config location.");
            case DELETE_TELEPORT_POINT -> AdminGuiItems.item(Material.ENDER_PEARL, ChatColor.RED,
                    "Delete '" + target + "'?", ChatColor.GRAY + "Removes only the saved config point.",
                    ChatColor.YELLOW + "World blocks remain unchanged.");
        };
    }

    private static String confirmationTitle(AdminMenuHolder.ConfirmationAction action) {
        return switch (action) {
            case START -> "Confirm Start";
            case STOP -> "Confirm Stop";
            case CLEAR_INFECTED_SPAWN -> "Confirm Clear Spawn";
            case DELETE_TELEPORT_POINT -> "Confirm Delete Point";
        };
    }

    private static String confirmLabel(AdminMenuHolder.ConfirmationAction action) {
        return switch (action) {
            case START -> "Confirm Start";
            case STOP -> "Confirm Stop";
            case CLEAR_INFECTED_SPAWN -> "Confirm Clear";
            case DELETE_TELEPORT_POINT -> "Confirm Delete";
        };
    }

    private static String startBlockedReason(AdminSetupService.SetupSnapshot snapshot, boolean running) {
        if (running) {
            return ChatColor.RED + "The event is already running.";
        }
        if (!snapshot.infectedSpawnConfigured()) {
            return ChatColor.RED + "Missing infected spawn.";
        }
        if (snapshot.teleportPointCount() == 0) {
            return ChatColor.RED + "Missing teleport points.";
        }
        return ChatColor.YELLOW + "Start is currently unavailable.";
    }

    private static String coordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String teamKey(PlayerEntry entry) {
        return entry.infected() ? "infected" : "survivor";
    }

    private static String stateKey(AdminSetupService.StoredLocation location) {
        return location.world()
                + '|' + Long.toUnsignedString(Double.doubleToLongBits(location.x()))
                + '|' + Long.toUnsignedString(Double.doubleToLongBits(location.y()))
                + '|' + Long.toUnsignedString(Double.doubleToLongBits(location.z()))
                + '|' + Integer.toUnsignedString(Float.floatToIntBits(location.yaw()))
                + '|' + Integer.toUnsignedString(Float.floatToIntBits(location.pitch()));
    }

    private static org.bukkit.inventory.ItemStack backItem() {
        return AdminGuiItems.item(Material.ARROW, ChatColor.AQUA, "Back",
                ChatColor.GRAY + "Return to the main control desk.");
    }

    private static org.bukkit.inventory.ItemStack closeItem() {
        return AdminGuiItems.item(Material.BARRIER, ChatColor.RED, "Close Menu",
                ChatColor.GRAY + "Close the Infected control desk.");
    }

    private static org.bukkit.inventory.ItemStack helpItem() {
        return AdminGuiItems.item(Material.KNOWLEDGE_BOOK, ChatColor.AQUA, "Help / Legend",
                ChatColor.GREEN + "Green: " + ChatColor.GRAY + "Ready or safe action",
                ChatColor.YELLOW + "Yellow: " + ChatColor.GRAY + "Warning or incomplete",
                ChatColor.RED + "Red: " + ChatColor.GRAY + "Blocked or destructive",
                ChatColor.AQUA + "Aqua: " + ChatColor.GRAY + "Navigation or information");
    }

    private static void addStandardFooter(Inventory menu, boolean back, boolean help) {
        if (back) {
            menu.setItem(AdminGuiLayout.BACK, backItem());
        }
        menu.setItem(AdminGuiLayout.CLOSE, closeItem());
        if (help) {
            menu.setItem(AdminGuiLayout.HELP, helpItem());
        }
    }

    private static void fillBorder(Inventory menu) {
        org.bukkit.inventory.ItemStack dark = AdminGuiItems.background(Material.BLACK_STAINED_GLASS_PANE);
        int rows = menu.getSize() / 9;
        for (int slot = 0; slot < menu.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                menu.setItem(slot, dark);
            }
        }
    }

    private static void fillTopAndBottom(Inventory menu) {
        org.bukkit.inventory.ItemStack dark = AdminGuiItems.background(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 9; slot++) {
            menu.setItem(slot, dark);
        }
        for (int slot = menu.getSize() - 9; slot < menu.getSize(); slot++) {
            menu.setItem(slot, dark);
        }
    }

    private static void fillAll(Inventory menu, Material material) {
        org.bukkit.inventory.ItemStack background = AdminGuiItems.background(material);
        for (int slot = 0; slot < menu.getSize(); slot++) {
            menu.setItem(slot, background);
        }
    }

    private static void playerOpen(Player player, Inventory inventory) {
        player.openInventory(inventory);
    }

    private record PlayerEntry(UUID id, Player player, boolean infected) {
    }

}
