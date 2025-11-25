package org.bloby.psychik;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Psychik extends JavaPlugin {
    private static Psychik instance;
    private ZoneManager zoneManager;
    
    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        zoneManager = new ZoneManager(this);
        zoneManager.loadZones();
        getCommand("zone").setExecutor(new ZoneCommand(this));
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getLogger().info("Psychik enabled!");
    }
    
    @Override
    public void onDisable() {
        if (zoneManager != null) zoneManager.saveZones();
        getLogger().info("Psychik disabled!");
    }
    
    public static Psychik getInstance() { return instance; }
    public ZoneManager getZoneManager() { return zoneManager; }
    
    public static class PsychikZone {
        public enum Shape { CIRCLE, SQUARE }
        private String name;
        private Location center;
        private Shape shape;
        private double size;
        private double gravityMultiplier = 1.0;
        private double speedMultiplier = 1.0;
        private double jumpMultiplier = 1.0;
        private double knockbackMultiplier = 1.0;
        private double staminaDrainPerSec = 0.0;
        
        public PsychikZone(String name, Location center, Shape shape, double size) {
            this.name = name;
            this.center = center;
            this.shape = shape;
            this.size = size;
        }
        
        public boolean contains(Location loc) {
            if (loc.getWorld() != center.getWorld()) return false;
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            return shape == Shape.CIRCLE ? (dx*dx + dz*dz) <= (size*size) : Math.abs(dx) <= size && Math.abs(dz) <= size;
        }
        
        public void saveToConfig(ConfigurationSection s) {
            s.set("center.world", center.getWorld().getName());
            s.set("center.x", center.getX());
            s.set("center.y", center.getY());
            s.set("center.z", center.getZ());
            s.set("shape", shape.name());
            s.set("size", size);
            s.set("gravity", gravityMultiplier);
            s.set("speed", speedMultiplier);
            s.set("jump", jumpMultiplier);
            s.set("knockback", knockbackMultiplier);
            s.set("stamina", staminaDrainPerSec);
        }
        
        public static PsychikZone loadFromConfig(String name, ConfigurationSection s) {
            World world = Bukkit.getWorld(s.getString("center.world"));
            if (world == null) return null;
            Location center = new Location(world, s.getDouble("center.x"), s.getDouble("center.y"), s.getDouble("center.z"));
            PsychikZone z = new PsychikZone(name, center, Shape.valueOf(s.getString("shape")), s.getDouble("size"));
            z.gravityMultiplier = s.getDouble("gravity", 1.0);
            z.speedMultiplier = s.getDouble("speed", 1.0);
            z.jumpMultiplier = s.getDouble("jump", 1.0);
            z.knockbackMultiplier = s.getDouble("knockback", 1.0);
            z.staminaDrainPerSec = s.getDouble("stamina", 0.0);
            return z;
        }
        
        public String getName() { return name; }
        public Location getCenter() { return center; }
        public Shape getShape() { return shape; }
        public double getSize() { return size; }
        public void setSize(double s) { size = s; }
        public double getGravityMultiplier() { return gravityMultiplier; }
        public void setGravityMultiplier(double v) { gravityMultiplier = v; }
        public double getSpeedMultiplier() { return speedMultiplier; }
        public void setSpeedMultiplier(double v) { speedMultiplier = v; }
        public double getJumpMultiplier() { return jumpMultiplier; }
        public void setJumpMultiplier(double v) { jumpMultiplier = v; }
        public double getKnockbackMultiplier() { return knockbackMultiplier; }
        public void setKnockbackMultiplier(double v) { knockbackMultiplier = v; }
        public double getStaminaDrainPerSec() { return staminaDrainPerSec; }
        public void setStaminaDrainPerSec(double v) { staminaDrainPerSec = v; }
    }
    
    public static class ZoneManager {
        private final Psychik plugin;
        private final Map<String, PsychikZone> zones = new HashMap<>();
        private final File zonesFile;
        
        public ZoneManager(Psychik plugin) {
            this.plugin = plugin;
            this.zonesFile = new File(plugin.getDataFolder(), "zones.yml");
        }
        
        public void loadZones() {
            if (!zonesFile.exists()) try { zonesFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(zonesFile);
            ConfigurationSection sec = cfg.getConfigurationSection("zones");
            if (sec == null) return;
            for (String n : sec.getKeys(false)) {
                PsychikZone z = PsychikZone.loadFromConfig(n, sec.getConfigurationSection(n));
                if (z != null) zones.put(n, z);
            }
            plugin.getLogger().info("Loaded " + zones.size() + " zones");
        }
        
        public void saveZones() {
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, PsychikZone> e : zones.entrySet()) {
                e.getValue().saveToConfig(cfg.createSection("zones." + e.getKey()));
            }
            try { cfg.save(zonesFile); } catch (IOException e) { plugin.getLogger().severe("Save failed: " + e.getMessage()); }
        }
        
        public void addZone(PsychikZone z) { zones.put(z.getName(), z); saveZones(); }
        public void removeZone(String n) { zones.remove(n); saveZones(); }
        public PsychikZone getZone(String n) { return zones.get(n); }
        public List<PsychikZone> getZonesAt(Location loc) {
            List<PsychikZone> r = new ArrayList<>();
            for (PsychikZone z : zones.values()) if (z.contains(loc)) r.add(z);
            return r;
        }
        public Map<String, PsychikZone> getAllZones() { return new HashMap<>(zones); }
    }
    
    public static class ZoneApplier {
        private static final Map<UUID, PsychikZone> playerZones = new HashMap<>();
        
        public static void apply(Player p, PsychikZone z) {
            playerZones.put(p.getUniqueId(), z);
            AttributeInstance spd = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.1 * z.getSpeedMultiplier());
            if (z.getGravityMultiplier() < 1.0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, (int)((1.0-z.getGravityMultiplier())*5), false, false, false));
            }
            if (z.getJumpMultiplier() != 1.0) {
                int amp = (int)((z.getJumpMultiplier()-1.0)*3);
                if (amp > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, amp, false, false, false));
            }
        }
        
        public static void remove(Player p) {
            playerZones.remove(p.getUniqueId());
            AttributeInstance spd = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.1);
            p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            p.removePotionEffect(PotionEffectType.JUMP);
        }
        
        public static PsychikZone getCurrent(Player p) { return playerZones.get(p.getUniqueId()); }
        
        public static void drainStamina(Player p, PsychikZone z) {
            if (z.getStaminaDrainPerSec() > 0) {
                float drain = (float)(z.getStaminaDrainPerSec() / 20.0);
                float sat = Math.max(0, p.getSaturation() - drain);
                p.setSaturation(sat);
                if (sat <= 0 && p.getFoodLevel() > 0) p.setFoodLevel(Math.max(0, p.getFoodLevel()-1));
            }
        }
    }
    
    public static class ZoneCommand implements CommandExecutor, TabCompleter {
        private final Psychik plugin;
        
        public ZoneCommand(Psychik p) { plugin = p; }
        
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!s.hasPermission("psychik.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
            if (a.length == 0) { sendHelp(s); return true; }
            switch (a[0].toLowerCase()) {
                case "create": return create(s, a);
                case "delete": return delete(s, a);
                case "edit": return edit(s, a);
                case "list": return list(s);
                case "info": return info(s, a);
                default: sendHelp(s); return true;
            }
        }
        
        private void sendHelp(CommandSender s) {
            s.sendMessage(ChatColor.GOLD + "=== Psychik Zones ===");
            s.sendMessage(ChatColor.YELLOW + "/zone create <n> <circle|square> <size>");
            s.sendMessage(ChatColor.YELLOW + "/zone delete <n>");
            s.sendMessage(ChatColor.YELLOW + "/zone edit <n> <prop> <val>");
            s.sendMessage(ChatColor.YELLOW + "/zone list");
            s.sendMessage(ChatColor.YELLOW + "/zone info <n>");
        }
        
        private boolean create(CommandSender s, String[] a) {
            if (!(s instanceof Player)) { s.sendMessage(ChatColor.RED + "Players only."); return true; }
            if (a.length < 4) { s.sendMessage(ChatColor.RED + "Usage: /zone create <n> <circle|square> <size>"); return true; }
            Player p = (Player)s;
            String n = a[1];
            double sz;
            try { sz = Double.parseDouble(a[3]); } catch (Exception e) { s.sendMessage(ChatColor.RED + "Invalid size."); return true; }
            if (plugin.getZoneManager().getZone(n) != null) { s.sendMessage(ChatColor.RED + "Zone exists."); return true; }
            PsychikZone.Shape sh;
            try { sh = PsychikZone.Shape.valueOf(a[2].toUpperCase()); } catch (Exception e) { s.sendMessage(ChatColor.RED + "Invalid shape."); return true; }
            plugin.getZoneManager().addZone(new PsychikZone(n, p.getLocation(), sh, sz));
            s.sendMessage(ChatColor.GREEN + "Zone '" + n + "' created.");
            return true;
        }
        
        private boolean delete(CommandSender s, String[] a) {
            if (a.length < 2) { s.sendMessage(ChatColor.RED + "Usage: /zone delete <n>"); return true; }
            if (plugin.getZoneManager().getZone(a[1]) == null) { s.sendMessage(ChatColor.RED + "Zone not found."); return true; }
            plugin.getZoneManager().removeZone(a[1]);
            s.sendMessage(ChatColor.GREEN + "Zone deleted.");
            return true;
        }
        
        private boolean edit(CommandSender s, String[] a) {
            if (a.length < 4) { s.sendMessage(ChatColor.RED + "Usage: /zone edit <n> <prop> <val>"); return true; }
            PsychikZone z = plugin.getZoneManager().getZone(a[1]);
            if (z == null) { s.sendMessage(ChatColor.RED + "Zone not found."); return true; }
            double v;
            try { v = Double.parseDouble(a[3]); } catch (Exception e) { s.sendMessage(ChatColor.RED + "Invalid value."); return true; }
            switch (a[2].toLowerCase()) {
                case "gravity": z.setGravityMultiplier(v); break;
                case "speed": z.setSpeedMultiplier(v); break;
                case "jump": z.setJumpMultiplier(v); break;
                case "knockback": z.setKnockbackMultiplier(v); break;
                case "stamina": z.setStaminaDrainPerSec(v); break;
                case "size": z.setSize(v); break;
                default: s.sendMessage(ChatColor.RED + "Unknown property."); return true;
            }
            plugin.getZoneManager().saveZones();
            s.sendMessage(ChatColor.GREEN + "Zone updated.");
            return true;
        }
        
        private boolean list(CommandSender s) {
            s.sendMessage(ChatColor.GOLD + "Zones: " + String.join(", ", plugin.getZoneManager().getAllZones().keySet()));
            return true;
        }
        
        private boolean info(CommandSender s, String[] a) {
            if (a.length < 2) { s.sendMessage(ChatColor.RED + "Usage: /zone info <n>"); return true; }
            PsychikZone z = plugin.getZoneManager().getZone(a[1]);
            if (z == null) { s.sendMessage(ChatColor.RED + "Zone not found."); return true; }
            s.sendMessage(ChatColor.GOLD + "Zone: " + z.getName());
            s.sendMessage(ChatColor.YELLOW + "Shape: " + z.getShape() + " Size: " + z.getSize());
            s.sendMessage(ChatColor.YELLOW + "Gravity: " + z.getGravityMultiplier() + " Speed: " + z.getSpeedMultiplier());
            s.sendMessage(ChatColor.YELLOW + "Jump: " + z.getJumpMultiplier() + " Knockback: " + z.getKnockbackMultiplier());
            s.sendMessage(ChatColor.YELLOW + "Stamina: " + z.getStaminaDrainPerSec());
            return true;
        }
        
        @Override
        public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
            List<String> r = new ArrayList<>();
            if (args.length == 1) r.addAll(Arrays.asList("create", "delete", "edit", "list", "info"));
            else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("edit")))
                r.addAll(plugin.getZoneManager().getAllZones().keySet());
            else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("create")) r.addAll(Arrays.asList("circle", "square"));
                else if (args[0].equalsIgnoreCase("edit")) r.addAll(Arrays.asList("gravity", "speed", "jump", "knockback", "stamina", "size"));
            }
            return r;
        }
    }
    
    public static class MovementListener implements Listener {
        private final Psychik plugin;
        
        public MovementListener(Psychik p) { plugin = p; }
        
        @EventHandler
        public void onMove(PlayerMoveEvent e) {
            Player p = e.getPlayer();
            List<PsychikZone> zones = plugin.getZoneManager().getZonesAt(p.getLocation());
            PsychikZone current = ZoneApplier.getCurrent(p);
            if (zones.isEmpty()) {
                if (current != null) {
                    ZoneApplier.remove(p);
                    p.sendMessage(ChatColor.GRAY + "Left zone: " + current.getName());
                }
            } else {
                PsychikZone newZone = zones.get(0);
                if (current == null || !current.equals(newZone)) {
                    if (current != null) ZoneApplier.remove(p);
                    ZoneApplier.apply(p, newZone);
                    p.sendMessage(ChatColor.GREEN + "Entered zone: " + newZone.getName());
                } else {
                    ZoneApplier.drainStamina(p, newZone);
                }
            }
        }
    }
}
