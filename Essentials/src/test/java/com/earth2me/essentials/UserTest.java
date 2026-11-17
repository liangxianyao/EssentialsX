package com.earth2me.essentials;

import com.earth2me.essentials.commands.Commandflytime;
import com.earth2me.essentials.metrics.MetricsWrapper;
import com.earth2me.essentials.utils.EnumUtil;
import net.ess3.api.MaxMoneyException;
import net.ess3.api.TranslatableException;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserTest {
    private static final Statistic PLAY_ONE_TICK = EnumUtil.getStatistic("PLAY_ONE_MINUTE", "PLAY_ONE_TICK");

    private PlayerMock base1;
    private Essentials ess;
    private ServerMock server;

    @BeforeEach
    public void setUp() {
        this.server = MockBukkit.mock();
        Essentials.TESTING = true;
        ess = MockBukkit.load(Essentials.class);
        base1 = server.addPlayer("testPlayer1");
        ess.getUser(base1);
    }

    @AfterEach
    public void tearEach() {
        Essentials.TESTING = true;
        MockBukkit.unmock();
    }

    private void should(final String what) {
        System.out.println("UserTest should " + what);
    }

    private void runFlytime(final User user, final String... args) throws Exception {
        final Commandflytime command = new Commandflytime();
        command.setEssentials(ess);
        command.run(server, user, "flytime", null, args);
    }

    private void enableFlytimePermissions() {
        base1.setOp(false);
        ess.getPermissionsHandler().checkPermissions();
        Essentials.TESTING = false;
        base1.addAttachment(ess, "essentials.flytime", true);
    }

    @Test
    public void testUpdate() {
        final Player base1alt = server.getPlayer(base1.getName());
        assertEquals(base1alt, ess.getUser(base1alt).getBase());
    }

    @Test
    public void testOfflinePlayerStubPreservesIdentityAndPermissions() {
        final UUID uuid = UUID.randomUUID();
        final Server mockServer = mock(Server.class);
        final OfflinePlayer offline = mock(OfflinePlayer.class);
        when(mockServer.getOfflinePlayer(uuid)).thenReturn(offline);
        when(mockServer.getWorlds()).thenReturn(server.getWorlds());
        when(offline.getUniqueId()).thenReturn(uuid);
        when(offline.getName()).thenReturn(null);

        final Player stub = OfflinePlayerStub.create(uuid, mockServer);
        OfflinePlayerStub.setName(stub, "offlineUser");

        assertTrue(OfflinePlayerStub.isStub(stub));
        assertFalse(OfflinePlayerStub.isStub(base1));
        assertFalse(OfflinePlayerStub.isStub(null));
        assertEquals(offline, OfflinePlayerStub.getOffline(stub));
        assertEquals(base1, OfflinePlayerStub.getOffline(base1));
        assertEquals(uuid, stub.getUniqueId());
        assertEquals("offlineUser", stub.getName());
        assertEquals(mockServer, stub.getServer());
        assertEquals(server.getWorlds().get(0), stub.getWorld());
        assertFalse(stub.isOnline());
        assertFalse(stub.hasPermission("essentials.flytime"));
        assertThrows(IllegalArgumentException.class, () -> OfflinePlayerStub.setName(base1, "invalid"));
    }

    @Test
    public void testHome() {
        final User user = ess.getUser(base1);
        final Location loc = base1.getLocation();
        loc.setWorld(server.getWorlds().get(0));
        user.setHome("home", loc);
        final Player base2 = server.getPlayer(base1.getName());
        final User user2 = ess.getUser(base2);

        final Location home = user2.getHome(loc);
        assertNotNull(home);
        assertEquals(loc.getWorld().getName(), home.getWorld().getName());
        assertEquals(loc.getX(), home.getX());
        assertEquals(loc.getY(), home.getY());
        assertEquals(loc.getZ(), home.getZ());
        assertEquals(loc.getYaw(), home.getYaw());
        assertEquals(loc.getPitch(), home.getPitch());
    }

    @Test
    public void testMoney() {
        should("properly set, take, give, and get money");
        final User user = ess.getUser(base1);
        BigDecimal i = new BigDecimal("100.5");
        try {
            user.setMoney(i);
            user.takeMoney(new BigDecimal(50));
            i = i.subtract(BigDecimal.valueOf(50));
            user.giveMoney(new BigDecimal(25));
            i = i.add(BigDecimal.valueOf(25));
        } catch (final MaxMoneyException ex) {
            fail();
        }

        assertEquals(user.getMoney(), i);
    }

    @Test
    public void testTimedFlightCanPauseAndResumeWhileOnlyOnlineTicksCount() {
        final User user = ess.getUser(base1);
        base1.setStatistic(PLAY_ONE_TICK, 100);
        user.setTimedFlightEndTick(300);

        user.pauseTimedFlight();
        assertEquals(0L, user.getTimedFlightEndTick());
        assertEquals(200L, user.getPausedTimedFlightTicks());

        base1.setStatistic(PLAY_ONE_TICK, 140);
        assertEquals(200L, user.getPausedTimedFlightTicks());
        user.resumeTimedFlight();
        assertEquals(340L, user.getTimedFlightEndTick());
        assertEquals(0L, user.getPausedTimedFlightTicks());
    }

    @Test
    public void testClearingTimedFlightAlsoClearsPausedBalance() {
        final User user = ess.getUser(base1);
        base1.setStatistic(PLAY_ONE_TICK, 100);
        user.setTimedFlightEndTick(300);
        user.pauseTimedFlight();

        user.clearTimedFlight();
        assertEquals(0L, user.getPausedTimedFlightTicks());
    }

    @Test
    public void testOfflineTimeDoesNotConsumeTimedFlight() {
        final User user = ess.getUser(base1);
        base1.setStatistic(PLAY_ONE_TICK, 100);
        user.setTimedFlightEndTick(300);

        assertEquals(200L, user.getRemainingTimedFlightTicks());
        assertTrue(base1.disconnect());
        assertEquals(0L, user.getRemainingTimedFlightTicks());
        server.addPlayer(base1);
        assertEquals(200L, user.getRemainingTimedFlightTicks());
    }

    @Test
    public void testTimedFlightRejectsZeroDuration() {
        final User user = ess.getUser(base1);
        assertThrows(IllegalArgumentException.class, () -> user.calculateTimedFlightEndTick(0));
    }

    @Test
    public void testTimedFlightRejectsPlaytimeOverflow() {
        final User user = ess.getUser(base1);
        base1.setStatistic(PLAY_ONE_TICK, Integer.MAX_VALUE - 10);
        assertThrows(ArithmeticException.class, () -> user.calculateTimedFlightEndTick(20));
    }

    @Test
    public void testExpiredTimedFlightDisablesFlightAndClearsState() {
        final User user = ess.getUser(base1);
        base1.setStatistic(PLAY_ONE_TICK, 300);
        base1.setAllowFlight(true);
        user.setTimedFlightEndTick(300);

        assertTrue(user.checkTimedFlightExpiry());
        assertFalse(base1.getAllowFlight());
        assertEquals(0L, user.getTimedFlightEndTick());
    }

    @Test
    public void testConfiguredFlightAllowanceIsClaimedOnlyOnce() throws Exception {
        final User user = ess.getUser(base1);
        enableFlytimePermissions();
        base1.addAttachment(ess, "essentials.flytime.duration.basic", true);
        base1.setStatistic(PLAY_ONE_TICK, 100);

        runFlytime(user);
        assertTrue(base1.getAllowFlight());
        assertEquals(12000L, user.getRemainingTimedFlightTicks());

        runFlytime(user);
        assertEquals(12000L, user.getPausedTimedFlightTicks());
        runFlytime(user);
        assertEquals(12000L, user.getRemainingTimedFlightTicks());

        base1.setStatistic(PLAY_ONE_TICK, 12100);
        assertTrue(user.checkTimedFlightExpiry());
        user.cleanup();
        final User reloaded = ess.getUser(base1);
        assertTrue(reloaded.hasClaimedTimedFlightAllowance());
        runFlytime(reloaded);
        assertEquals(0L, reloaded.getRemainingTimedFlightTicks());
        assertFalse(base1.getAllowFlight());
    }

    @Test
    public void testLongestConfiguredFlightAllowanceWins() throws Exception {
        final User user = ess.getUser(base1);
        enableFlytimePermissions();
        base1.addAttachment(ess, "essentials.flytime.duration.basic", true);
        base1.addAttachment(ess, "essentials.flytime.duration.vip", true);

        runFlytime(user);
        assertEquals(72000L, user.getRemainingTimedFlightTicks());
    }

    @Test
    public void testPlayersCannotGrantThemselvesArbitraryFlightTime() throws Exception {
        final User user = ess.getUser(base1);
        enableFlytimePermissions();
        base1.addAttachment(ess, "essentials.flytime.duration.basic", true);

        runFlytime(user, "1d");
        assertEquals(0L, user.getRemainingTimedFlightTicks());
        assertFalse(base1.getAllowFlight());
    }

    @Test
    public void testAdministratorCanReplenishSpentFlightTime() throws Exception {
        final User user = ess.getUser(base1);
        enableFlytimePermissions();
        base1.addAttachment(ess, "essentials.flytime.others", true);

        runFlytime(user, "1m");
        assertEquals(1200L, user.getRemainingTimedFlightTicks());
    }

    @Test
    public void testRankPermissionWithoutCommandPermissionCannotClaim() throws Exception {
        final User user = ess.getUser(base1);
        base1.setOp(false);
        ess.getPermissionsHandler().checkPermissions();
        Essentials.TESTING = false;
        base1.addAttachment(ess, "essentials.flytime.duration.basic", true);
        final Field metricsField = Essentials.class.getDeclaredField("metrics");
        metricsField.setAccessible(true);
        metricsField.set(ess, mock(MetricsWrapper.class));

        server.dispatchCommand(base1, "flytime");
        assertEquals(0L, user.getRemainingTimedFlightTicks());
    }

    @Test
    public void testMissingOrInvalidRankCannotGrantAllowance() throws Exception {
        final User user = ess.getUser(base1);
        enableFlytimePermissions();
        runFlytime(user);
        assertEquals(0L, user.getRemainingTimedFlightTicks());

        final Path configFile = ess.getSettings().getConfigFile().toPath();
        final String contents = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
        assertTrue(contents.contains("basic: 10m"));
        Files.write(configFile, contents.replace("basic: 10m", "basic: invalid").getBytes(StandardCharsets.UTF_8));
        ess.getSettings().reloadConfig();
        base1.addAttachment(ess, "essentials.flytime.duration.basic", true);
        runFlytime(user);
        assertEquals(0L, user.getRemainingTimedFlightTicks());
    }

    @Test
    public void testInvalidAdministratorDurationCannotGrantTime() {
        final User user = ess.getUser(base1);
        enableFlytimePermissions();
        base1.addAttachment(ess, "essentials.flytime.others", true);

        assertThrows(TranslatableException.class, () -> runFlytime(user, "0m"));
        assertThrows(TranslatableException.class, () -> runFlytime(user, "999999999999999999999h"));
        assertEquals(0L, user.getRemainingTimedFlightTicks());
    }
}
