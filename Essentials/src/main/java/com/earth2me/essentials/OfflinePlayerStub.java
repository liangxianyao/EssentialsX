package com.earth2me.essentials;

import org.bukkit.BanList;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adapts an offline player to the Player type used by Essentials' user storage. */
public final class OfflinePlayerStub implements InvocationHandler {
    private final OfflinePlayer base;
    private final Server server;
    private String name;
    private Location location = new Location(null, 0, 0, 0);
    private World world;
    private boolean allowFlight;
    private boolean flying;

    private OfflinePlayerStub(final OfflinePlayer base, final Server server) {
        this.base = base;
        this.server = server;
        this.name = base.getName();
        final List<World> worlds = server.getWorlds();
        this.world = worlds.isEmpty() ? null : worlds.get(0);
    }

    public static Player create(final UUID uuid, final Server server) {
        return create(server.getOfflinePlayer(uuid), server);
    }

    public static Player create(final String name, final Server server) {
        final OfflinePlayerStub stub = new OfflinePlayerStub(server.getOfflinePlayer(name), server);
        stub.setName(name);
        return stub.asPlayer();
    }

    private static Player create(final OfflinePlayer base, final Server server) {
        return new OfflinePlayerStub(base, server).asPlayer();
    }

    private Player asPlayer() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, this);
    }

    private static OfflinePlayerStub handler(final Player player) {
        if (player != null && Proxy.isProxyClass(player.getClass())) {
            final InvocationHandler handler = Proxy.getInvocationHandler(player);
            if (handler instanceof OfflinePlayerStub) {
                return (OfflinePlayerStub) handler;
            }
        }
        return null;
    }

    public static boolean isStub(final Player player) {
        return handler(player) != null;
    }

    public static OfflinePlayer getOffline(final Player player) {
        final OfflinePlayerStub stub = handler(player);
        return stub == null ? player : stub.base;
    }

    public static void setName(final Player player, final String name) {
        final OfflinePlayerStub stub = handler(player);
        if (stub == null) {
            throw new IllegalArgumentException("Player is not an offline stub");
        }
        stub.setName(name);
    }

    private void setName(final String fallback) {
        this.name = base.getName() == null ? fallback : base.getName();
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) {
        switch (method.getName()) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "OfflinePlayerStub{" + name + "}";
            case "getName": return name;
            case "getDisplayName": return base.getName();
            case "getUniqueId": return base.getUniqueId();
            case "getServer": return server;
            case "getWorld": return world;
            case "getLocation": return location;
            case "getEyeLocation": return location;
            case "getEntityId": return -1;
            case "isOnline": return base.isOnline();
            case "isOp": return base.isOp();
            case "setOp": base.setOp((Boolean) args[0]); return null;
            case "isWhitelisted": return base.isWhitelisted();
            case "setWhitelisted": base.setWhitelisted((Boolean) args[0]); return null;
            case "isBanned":
                if (base.getName() == null && name != null) {
                    return server.getBanList(BanList.Type.NAME).isBanned(name);
                }
                return base.isBanned();
            case "getPlayer": return base.getPlayer();
            case "getFirstPlayed": return base.getFirstPlayed();
            case "getLastPlayed": return base.getLastPlayed();
            case "hasPlayedBefore": return base.hasPlayedBefore();
            case "serialize": return base.serialize();
            case "getAllowFlight": return allowFlight;
            case "setAllowFlight": allowFlight = (Boolean) args[0]; return null;
            case "isFlying": return flying;
            case "setFlying": flying = (Boolean) args[0]; return null;
            case "getFlySpeed": return 0.1f;
            case "getWalkSpeed": return 0.2f;
            case "getPlayerTime": return 0L;
            case "teleport":
                if (args != null && args.length > 0 && args[0] instanceof Location) {
                    location = (Location) args[0];
                    world = location.getWorld();
                    return true;
                }
                return false;
            default: return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(final Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        if (Set.class.isAssignableFrom(type)) return Collections.emptySet();
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) return Collections.emptyList();
        if (Map.class.isAssignableFrom(type)) return Collections.emptyMap();
        return null;
    }
}
