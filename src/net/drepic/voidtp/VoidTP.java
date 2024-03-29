package net.drepic.voidtp;

import java.io.File;
import java.util.HashSet;

import net.drepic.voidtp.config.Settings;
import net.drepic.voidtp.config.compatlayer.CompatConfig;
import net.drepic.voidtp.config.compatlayer.CompatConfigFactory;
import net.drepic.voidtp.config.compatlayer.ConfigUtil;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;

public class VoidTP extends JavaPlugin implements Listener{
	/** Ok to teleport on.*/
	final HashSet<Integer> safeStand = new HashSet<Integer>();

	/** Ok to teleport into.*/
	final HashSet<Integer> safeTransparent = new HashSet<Integer>();

	final HashSet<String> exemptFall = new HashSet<String>();

	private final Settings settings = new Settings();

	public VoidTP(){
		super();
		for (int x : new int[]{
				1,2,3,4,5,
				7,8,9,
				12,13,14,15,16,
				17,18,19,20,21,22,
				23,24,25,29,33,
				35,41,42,43,44,45,46,47,48,49,
				52,53,54,56,57,58,60,61,62,
				67,73,74,79, 80, 82,84,86,87,88,
				89, 91, 95, 97,98,99,100,103,
				108, 109, 110, 111, 112, 114, 116,
				118, 120, 121, 123, 124, 125, 126,
				128, 129, 130, 133, 134,135,137,138,

		}){
			safeStand.add(x);
		}

		for (int x : new int[]{
				6, 27, 28, 26,
				31, 30, 32, 37, 38, 39, 40,
				50, 55, 59, 63, 64, 65, 66, 68, 69, 
				71, 75, 76, 77 , 78, 83, 93, 94,
				104, 105, 106, 115, 117, 127,

		}){
			safeTransparent.add(x);
		}
	}

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		loadSettings();
		super.onEnable();
	}

	public void loadSettings(){
		CompatConfig cfg = CompatConfigFactory.getConfig(new File(getDataFolder(), "config.yml"));
		cfg.load();
		if (ConfigUtil.forceDefaults(Settings.getDefaultConfig(), cfg)) cfg.save();
		settings.fromConfig(cfg);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		if (command != null) label = command.getLabel();
		label = label.trim().toLowerCase();
		int len = args.length;
		if (label.equals("voidtp")) return onManageCommand(sender, args, len);
		return false;
	}

	private boolean onManageCommand(CommandSender sender, String[] args, int len) {
		String cmd = null;
		if (len > 0) cmd = args[0].trim().toLowerCase();
		if (len == 1 && cmd.equals("reload")){
			if (!checkPermission(sender, "voidtp.reload")) return true;
			loadSettings();
			sender.sendMessage("[VoidTP] Settings reloaded.");
			return true;
		}
		return false;
	}

	public static final boolean hasPermission(final CommandSender sender, final String permission){
		return sender.isOp() || sender.hasPermission(permission);
	}

	/**
	 * Check and message on failure.
	 * @param sender
	 * @param permission
	 * @return
	 */
	public static final boolean checkPermission(final CommandSender sender, final String permission){
		if (hasPermission(sender, permission)) return true;
		sender.sendMessage("You don't have permission.");
		return false;
	}

	@EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
	final void onDamage(final EntityDamageEvent event){
		if (event.getEntityType() != EntityType.PLAYER) return;
		if (event.getCause() == DamageCause.FALL)
			if (checkFall(event.getEntity())) event.setCancelled(true);
		if (event.getCause() == DamageCause.VOID) 
			if (checkVoid(event.getEntity())) event.setCancelled(true);

	}

	private final boolean checkFall(final Entity entity) {
		if (!(entity instanceof Player)) return false;
		final Player player = (Player) entity;
//		System.out.println("fall: " + player.getTicksLived());
		if (exemptFall.remove(player.getName())) return true;
		else return false;
	}

	private final boolean checkVoid(final Entity entity) {
		if (!(entity instanceof Player)) return false;
		final Player player = (Player) entity;
//		System.out.println("void: " + player.getTicksLived());
		exemptFall.add(player.getName());
		final Location loc = player.getLocation();
		final World world = loc.getWorld();
		final String worldName = world.getName();
		if (settings.excludeExact.contains(worldName)) return false;
		else{
			for (final String suffix : settings.excludeSuffix){
				if (worldName.endsWith(suffix)) return false;
			}
		}
		final Chunk chunk = loc.getChunk();
		if (!chunk.isLoaded()) chunk.load();
		final Block block = world.getHighestBlockAt(loc.getBlockX(), loc.getBlockZ());
		final Location target = getSafeLocation(player, block);
		if (target == null){
			teleport(player, player.getWorld().getSpawnLocation());
			checkKick(player);
		}
		else{
			teleportSafe(player, target);
			checkKick(player);
		}
//		player.setFallDistance(0); // <- not good (NoCheatPlus)
		return true;
	}

	/**
	 * Assumes that above the location there is enough air / ok to tp on space. 
	 * @param block
	 * @return
	 */
	private Location getSafeLocation(final Player player, Block block) {
		while (block.getY() > 0){
			final int id = block.getTypeId();
			if (safeStand.contains(id)) return new Location(block.getWorld(), 0.5 + block.getX(), 1.0 + block.getY(), 0.5 + block.getZ());
			else if (safeTransparent.contains(id)) block = block.getRelative(BlockFace.DOWN);
			else return null;
		}
		return null;
	}

	private void checkKick(Player player) {
		if (!player.isOnline() || Bukkit.getPlayerExact(player.getName()) == null){
			player.kickPlayer("You are not online anyway.");
			player.remove();
		}
	}

	private void teleportSafe(Player player, Location location) {
		if (!teleport(player, location)){
			Location spawnLoc =  player.getWorld().getSpawnLocation();
			Location loc = getSafeLocation(player, spawnLoc.getBlock().getRelative(BlockFace.UP)); // not that much safe.
			if (loc == null){
				loc = getSafeLocation(player, spawnLoc.getWorld().getHighestBlockAt(spawnLoc));
				if (loc == null) loc = spawnLoc;
			}
			teleport(player, loc);
		}
	}

	private boolean teleport(Player player, Location location) {
		Location pLoc = player.getLocation();
		if (!location.getChunk().isLoaded()) location.getChunk().load();
		final Block block = location.getBlock().getRelative(BlockFace.DOWN);
		if (player.isOnline()) player.sendBlockChange(block.getLocation(), block.getTypeId(), block.getData());
		boolean res =  player.teleport(location);
		if (res && settings.log) getLogger().info("[VoidTP] Teleport player " + player.getName()+ " from " + pLoc.toString() + " to " + location);
		return res;
	}
}