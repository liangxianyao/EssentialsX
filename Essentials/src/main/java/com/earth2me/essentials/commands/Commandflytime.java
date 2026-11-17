package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.Settings;
import com.earth2me.essentials.User;
import com.earth2me.essentials.utils.DateUtil;
import net.ess3.api.TranslatableException;
import net.ess3.api.events.FlyStatusChangeEvent;
import org.bukkit.GameMode;
import org.bukkit.Server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Commandflytime extends EssentialsCommand {

    public Commandflytime() {
        super("flytime");
    }

    @Override
    protected void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        if (args.length != 2) {
            throw new NotEnoughArgumentsException();
        }
        grant(sender, getPlayer(server, sender, args, 1), parseTicks(args[0]), false);
    }

    @Override
    protected void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        if (args.length == 0) {
            toggle(user);
            return;
        }
        if (args.length > 2) {
            throw new NotEnoughArgumentsException();
        }
        if (!user.isAuthorized("essentials.flytime.others")) {
            user.sendTl("noAccessCommand");
            return;
        }
        final User target;
        if (args.length == 2) {
            target = getPlayer(server, user, args, 1);
        } else {
            target = user;
        }
        grant(user.getSource(), target, parseTicks(args[0]), false);
    }

    private void toggle(final User user) throws TranslatableException {
        user.checkTimedFlightExpiry();
        if (user.hasTimedFlight()) {
            final FlyStatusChangeEvent event = new FlyStatusChangeEvent(user, user, false);
            ess.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            user.pauseTimedFlight();
            user.getBase().setFallDistance(0f);
            if (user.getBase().getGameMode() != GameMode.CREATIVE && user.getBase().getGameMode() != GameMode.SPECTATOR) {
                user.getBase().setFlying(false);
                user.getBase().setAllowFlight(false);
            }
            user.sendTl("flytimePaused", Math.max(1L, user.getPausedTimedFlightTicks() / 20L));
            return;
        }
        if (user.getPausedTimedFlightTicks() <= 0) {
            if (user.getTimedFlightEndTick() > 0) {
                return;
            }
            if (!user.hasClaimedTimedFlightAllowance()) {
                final long durationTicks = ((Settings) ess.getSettings()).getFlytimePermissionDurationTicks(user);
                if (durationTicks > 0) {
                    grant(user.getSource(), user, durationTicks, true);
                    return;
                }
            }
            user.sendTl("flytimeNoTime");
            return;
        }
        try {
            user.calculateTimedFlightEndTick(user.getPausedTimedFlightTicks());
        } catch (final ArithmeticException ex) {
            throw new TranslatableException("illegalDate");
        }
        final FlyStatusChangeEvent event = new FlyStatusChangeEvent(user, user, true);
        ess.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        user.resumeTimedFlight();
        user.getBase().setFallDistance(0f);
        user.getBase().setAllowFlight(true);
        user.sendTl("flytimeResumed", Math.max(1L, user.getRemainingTimedFlightTicks() / 20L));
    }

    private long parseTicks(final String input) throws TranslatableException {
        try {
            return DateUtil.parseOnlineDurationTicks(input);
        } catch (final IllegalArgumentException | ArithmeticException ex) {
            throw new TranslatableException("illegalDate");
        }
    }

    private void grant(final CommandSource sender, final User target, final long durationTicks, final boolean claimAllowance) throws TranslatableException {
        target.checkTimedFlightExpiry();
        if (target.getPausedTimedFlightTicks() > 0) {
            final long pausedTicks;
            try {
                pausedTicks = Math.addExact(target.getPausedTimedFlightTicks(), durationTicks);
                target.calculateTimedFlightEndTick(pausedTicks);
            } catch (final ArithmeticException ex) {
                throw new TranslatableException("illegalDate");
            }
            target.setTimedFlightState(0, pausedTicks, claimAllowance);
            sendGrantMessage(sender, target, "flytimeAddedPaused", pausedTicks);
            return;
        }
        if (!target.hasTimedFlight() && target.getBase().getAllowFlight()) {
            sender.sendTl("flytimeAlreadyFlying", target.getDisplayName());
            return;
        }
        final long endTick;
        try {
            endTick = target.calculateTimedFlightEndTick(durationTicks);
        } catch (final ArithmeticException ex) {
            throw new TranslatableException("illegalDate");
        }
        if (!target.getBase().getAllowFlight()) {
            final FlyStatusChangeEvent event = new FlyStatusChangeEvent(target,
                sender.isPlayer() ? ess.getUser(sender.getPlayer()) : null, true);
            ess.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
        }
        target.setTimedFlightState(endTick, 0, claimAllowance);
        target.getBase().setFallDistance(0f);
        target.getBase().setAllowFlight(true);
        sendGrantMessage(sender, target, "flytimeAdded", target.getRemainingTimedFlightTicks());
    }

    private void sendGrantMessage(final CommandSource sender, final User target, final String message, final long ticks) {
        final long remainingSeconds = Math.max(1L, ticks / 20L);
        sender.sendTl(message, target.getDisplayName(), remainingSeconds);
        if (!sender.isPlayer() || !sender.getPlayer().equals(target.getBase())) {
            target.sendTl(message, target.getDisplayName(), remainingSeconds);
        }
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final CommandSource sender,
                                                 final String commandLabel, final String[] args) {
        if (args.length == 1 && (!sender.isPlayer() || ess.getUser(sender.getPlayer()).isAuthorized("essentials.flytime.others"))) {
            return Arrays.asList("10m", "1h", "1d");
        }
        if (args.length == 2 && (!sender.isPlayer() || ess.getUser(sender.getPlayer()).isAuthorized("essentials.flytime.others"))) {
            return getPlayers(sender);
        }
        return Collections.emptyList();
    }
}
