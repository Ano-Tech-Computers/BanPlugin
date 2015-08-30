package no.atc.floyd.bukkit.ban;




import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import java.sql.*;

/**
* Approve plugin for Bukkit
*
* @author FloydATC
*/
public class BanPlugin extends JavaPlugin implements Listener {

	public static final String MSG_PREFIX = ChatColor.GRAY + "[" + ChatColor.GOLD + "Ban" + ChatColor.GRAY + "] ";
	public static final ChatColor COLOR_INFO = ChatColor.AQUA;
	public static final ChatColor COLOR_WARNING = ChatColor.RED;
	public static final ChatColor COLOR_NAME = ChatColor.GOLD;
	
    private final ConcurrentHashMap<Player, Boolean> debugees = new ConcurrentHashMap<Player, Boolean>();
    public final ConcurrentHashMap<String, String> settings = new ConcurrentHashMap<String, String>();

    public static DbPool dbpool = null;
    
    String baseDir = "plugins/BanPlugin";
    String configFile = "settings.txt";

	public static final Logger logger = Logger.getLogger("Minecraft.BanPlugin");
    
//    public BanPlugin(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
//        super(pluginLoader, instance, desc, folder, plugin, cLoader);
//        // TODO: Place any custom initialization code here
//
//        // NOTE: Event registration should be done in onEnable not here as all events are unregistered when a plugin is disabled
//    }

    public void onDisable() {
        // TODO: Place any custom disable code here
    	
        // NOTE: All registered events are automatically unregistered when a plugin is disabled
    	
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
    	PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is disabled!" );
    }

    public void onEnable() {
        // TODO: Place any custom enable code here including the registration of any events

    	loadSettings();
    	initDbPool();
        	
    	// Register event handlers
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
    	
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args ) {
    	String cmdname = cmd.getName().toLowerCase();
        Player player = null;
        String pname = "(Console)";
        if (sender instanceof Player) {
        	player = (Player)sender;
        	pname = player.getName();
        }
        Connection dbh = null;
        
        if (cmdname.equalsIgnoreCase("bp")) {
        	
        	// Reload
    		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    			if (player == null || player.isOp() || player.hasPermission("BanPlugin.reload")) {
        			respond(player, MSG_PREFIX+COLOR_INFO+"Reloading configuration file");
        			loadSettings();
        			initDbPool();
        			return true;
        		} else {
        			logger.info(MSG_PREFIX+COLOR_WARNING+pname+" tried to reload but does not have permission");
        			return true;
        		}
        	}
    		return false;
        }

        if (cmdname.equalsIgnoreCase("warn")) {
        	// Warn player 
        	if (player == null || player.isOp() || player.hasPermission("BanPlugin.warn")) {
            	if (args.length < 1) {
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Need player name and a reason");
        			return false;
            	}
            	Player p = null;
            	String p_name = args[0];
            	String reason = "";
            	if (args.length > 1) {
	            	for (Integer i=1; i<args.length; i++) {
	            		if (!reason.isEmpty()) { reason = reason + " "; }
	            		reason = reason + args[i];
	            	}
            	}
            	p = getServer().getPlayer(p_name);
            	if (p != null) {
            		respond(player, MSG_PREFIX+COLOR_INFO+"Warned "+COLOR_NAME+p.getName());
            		respond(p, MSG_PREFIX+COLOR_WARNING+"YOU HAVE BEEN WARNED, FOLLOW THE RULES OR LEAVE");
            		if (!reason.isEmpty()) {
            			respond(p, MSG_PREFIX+reason);
            			logger.info(MSG_PREFIX+COLOR_NAME+pname+COLOR_INFO+" warned "+COLOR_NAME+p.getName()+COLOR_INFO+": "+reason);
            		} else {
            			logger.info(MSG_PREFIX+COLOR_NAME+pname+COLOR_INFO+" warned "+COLOR_NAME+p.getName());
            		}
            	} else {
            		respond(player, MSG_PREFIX+COLOR_WARNING+"Not logged in: "+COLOR_NAME+p_name);
            	}
        	}
        	return true;
        }

        if (cmdname.equalsIgnoreCase("kick")) {
        	// Kick player
        	if (player == null || player.isOp() || player.hasPermission("BanPlugin.kick")) {
            	if (args.length < 1) {
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Need player name and a reason");
        			return false;
            	}
            	Player p = null;
            	String p_name = args[0];
            	String reason = "";
            	if (args.length > 1) {
	            	for (Integer i=1; i<args.length; i++) {
	            		if (!reason.isEmpty()) { reason = reason + " "; }
	            		reason = reason + args[i];
	            	}
            	}
            	p = getServer().getPlayer(p_name);
            	if (p != null) {
            		respond(player, MSG_PREFIX+COLOR_INFO+"Kicked "+COLOR_NAME+p.getName());
            		if (!reason.isEmpty()) {
            			p.kickPlayer("Kicked by "+COLOR_NAME+pname+COLOR_INFO+": "+reason);
            			logger.info(MSG_PREFIX+COLOR_NAME+pname+COLOR_INFO+" kicked "+COLOR_NAME+p.getName()+COLOR_INFO+": "+reason);
            		} else {
            			p.kickPlayer("Kicked by "+COLOR_NAME+player.getName());
            			logger.info(MSG_PREFIX+COLOR_NAME+pname+COLOR_INFO+" kicked "+COLOR_NAME+p.getName());
            		}
            	} else {
            		respond(player, MSG_PREFIX+COLOR_WARNING+"Not logged in: "+COLOR_NAME+p_name);
            	}
        	}
        	return true;
        }

        if (cmdname.equalsIgnoreCase("permban")) {
        	// Permanently ban named player by name (and if possible, UUID and IP)
        	if (player == null || player.isOp() || player.hasPermission("BanPlugin.permban")) {
            	if (args.length < 2) {
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Need player name and a reason");
        			return false;
            	}
            	Player p = null;
            	String p_name = args[0];
            	String reason = "";
            	for (Integer i=1; i<args.length; i++) {
            		if (!reason.isEmpty()) { reason = reason + " "; }
            		reason = reason + args[i];
            	}
            	p = getServer().getPlayerExact(p_name);
        		if (dbpool == null) {
        			logger.info(MSG_PREFIX+COLOR_INFO+"Retrying dbpool initialization...");
        			initDbPool();
        		}
	       	    if (dbpool != null) { 
	       	        dbh = dbpool.getConnection();
	       	        if (dbh != null) {
	                	String p_uuid = uuid_by_name(dbh, p_name);
	                	String p_ip = ip_by_name(dbh, p_name);
	                	if (p != null) {
	                		// Yay! Player is online, get all his details
	                		p_uuid = p.getUniqueId().toString();
	                		p_ip = p.getAddress().getAddress().getHostAddress();
	                	}
	       	        	permBan(dbh, p_name, p_uuid, p_ip, pname, reason);
	        			respond(player, MSG_PREFIX+COLOR_NAME+p_name+COLOR_INFO+" permanently banned");
	       	        	if (p != null) {
		        			logger.warning(MSG_PREFIX+COLOR_INFO+"Kickbanned player "+COLOR_NAME+p_name);
	       	        		p.kickPlayer("Banned by "+COLOR_NAME+player+COLOR_INFO+": "+reason);
	       	        	}
		       	        dbpool.releaseConnection(dbh);
	       	        } else {
	        			logger.warning(MSG_PREFIX+COLOR_WARNING+"permban: dbh unavailable");
	        			respond(player, MSG_PREFIX+COLOR_WARNING+"Sorry, dbh unavailable");
	       	        }
        		} else {
        			logger.warning(MSG_PREFIX+COLOR_WARNING+"permban: dbpool unavailable");
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Sorry, dbpool unavailable");
        		}
        	}
        	return true;
        }

        if (cmdname.equalsIgnoreCase("tempban")) {
        	// Temporarily ban named player by name and UUID
        	if (player == null || player.isOp() || player.hasPermission("BanPlugin.tempban")) {
            	if (args.length < 3) {
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Need player name, duration and a reason");
        			return false;
            	}
            	Player p = null;
            	String p_name = args[0];
            	String duration = args[1];
            	
            	Calendar cal = Calendar.getInstance();
            	cal.getTime();
            	SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            	
            	Integer count = null;
            	String unit = null;
            	String expires = null;
            	Boolean valid = false;
            	Pattern pattern = Pattern.compile("^([0-9]+)([a-zA-Z])$");
            	Matcher matcher = pattern.matcher(duration);
            	if (matcher.find()) {
            		count = Integer.parseInt(matcher.group(1));
            		unit = matcher.group(2);
            		if (unit.equals("h")) {
            			valid = true;
            			cal.add(Calendar.HOUR_OF_DAY, count);
            		}
            		if (unit.equals("d")) {
            			valid = true;
            			cal.add(Calendar.DAY_OF_MONTH, count);
            		}
            		if (unit.equals("w")) {
            			valid = true;
            			cal.add(Calendar.WEEK_OF_YEAR, count);
            		}
            		if (valid == true) {
            			expires = template.format(cal.getTime());
            		} else {
            			respond(player, MSG_PREFIX+COLOR_WARNING+"Duration must be an integer immediately followed by 'h', 'd' or 'w'.");
            			return false;
            		}
            	} else {
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Duration must be an integer immediately followed by 'h', 'd' or 'w'.");
        			return false;
            	}
            	
            	String reason = "";
            	for (Integer i=2; i<args.length; i++) {
            		if (!reason.isEmpty()) { reason = reason + " "; }
            		reason = reason + args[i];
            	}
            	p = getServer().getPlayerExact(p_name);
        		if (dbpool == null) {
        			logger.info(MSG_PREFIX+COLOR_WARNING+"Retrying dbpool initialization...");
        			initDbPool();
        		}
	       	    if (dbpool != null) { 
	       	        dbh = dbpool.getConnection();
	       	        if (dbh != null) {
	                	String p_uuid = uuid_by_name(dbh, p_name);
	                	String p_ip = ip_by_name(dbh, p_name);
	                	if (p != null) {
	                		// Yay! Player is online, get all his details
	                		p_uuid = p.getUniqueId().toString();
	                		p_ip = p.getAddress().getAddress().getHostAddress();
	                	}
	       	        	tempBan(dbh, p_name, p_uuid, p_ip, pname, reason, expires);
	        			respond(player, MSG_PREFIX+COLOR_NAME+p_name+COLOR_INFO+" banned until "+expires);
	       	        	if (p != null) {
		        			logger.warning(MSG_PREFIX+COLOR_INFO+"Kickbanned player "+COLOR_NAME+p_name);
	       	        		p.kickPlayer("Banned until "+duration+" by "+COLOR_NAME+player+COLOR_INFO+": "+reason);
	       	        	}
		       	        dbpool.releaseConnection(dbh);
	       	        } else {
	        			logger.warning(MSG_PREFIX+COLOR_WARNING+"tempban: dbh unavailable");
	        			respond(player, MSG_PREFIX+COLOR_WARNING+"Sorry, dbh unavailable");
	       	        }
        		} else {
        			logger.warning(MSG_PREFIX+COLOR_WARNING+"tempban: dbpool unavailable");
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Sorry, dbpool unavailable");
        		}
        	}
        	return true;
        }

        if (cmdname.equalsIgnoreCase("unban")) {
        	// Pardon named player by name and UUID
        	if (player == null || player.isOp() || player.hasPermission("BanPlugin.unban")) {
            	if (args.length < 2) {
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Need player name and a reason");
        			return false;
            	}
            	String p_name = args[0];
            	String reason = "";
            	for (Integer i=1; i<args.length; i++) {
            		if (!reason.isEmpty()) { reason = reason + " "; }
            		reason = reason + args[i];
            	}
        		if (dbpool == null) {
        			logger.info(MSG_PREFIX+COLOR_WARNING+"Retrying dbpool initialization...");
        			initDbPool();
        		}
	       	    if (dbpool != null) { 
	       	        dbh = dbpool.getConnection();
	       	        if (dbh != null) {
	                	String p_uuid = uuid_by_name(dbh, p_name);
	                	String p_ip = ip_by_name(dbh, p_name);
	       	        	unBan(dbh, p_name, p_uuid, p_ip, pname, reason);
	        			respond(player, MSG_PREFIX+COLOR_NAME+p_name+COLOR_INFO+" pardoned");
		       	        dbpool.releaseConnection(dbh);
	       	        } else {
	        			logger.warning(MSG_PREFIX+COLOR_WARNING+"unban: dbh unavailable");
	        			respond(player, MSG_PREFIX+COLOR_WARNING+"Sorry, dbh unavailable");
	       	        }
        		} else {
        			logger.warning(MSG_PREFIX+COLOR_WARNING+"unban: dbpool unavailable");
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Sorry, dbpool unavailable");
        		}
        	}
        	return true;
        }

        if (cmdname.equalsIgnoreCase("checkban")) {
        	// Check active bans on a named player
        	if (player == null || player.isOp() || player.hasPermission("BanPlugin.checkban")) {
            	if (args.length != 1) {
        			respond(player, MSG_PREFIX+COLOR_WARNING+"Need player name");
        			return false;
            	}
            	String p_name = args[0];
        		if (dbpool == null) {
        			logger.info(MSG_PREFIX+COLOR_WARNING+"Retrying dbpool initialization...");
        			initDbPool();
        		}
	       	    if (dbpool != null) { 
	       	        dbh = dbpool.getConnection();
	       	        if (dbh != null) {
		        		try {
		        	        PreparedStatement sth;
		        	        sth = dbh.prepareStatement(
		        	          "SELECT player_name, type, issued_by, expires, reason FROM bans " +
		        	          "WHERE player_name LIKE ? " +
		        	          "ORDER BY player_name, issued"
		        	        );
		           			sth.setNString(1, p_name);
		           			ResultSet result = sth.executeQuery();
		           			Integer count = 0;
		           			while (result.next()) {
		           				count++;
		           				if (result.getString("type").equalsIgnoreCase("permban")) {
		                			respond(player, MSG_PREFIX+COLOR_NAME+result.getString("player_name")+COLOR_INFO+" banned by "+COLOR_NAME+result.getString("issued_by")+COLOR_INFO+": "+result.getString("reason"));
		           					continue;
		           				}
		           				if (result.getString("type").equalsIgnoreCase("tempban")) {
		                			respond(player, MSG_PREFIX+COLOR_NAME+result.getString("player_name")+COLOR_INFO+" banned by "+COLOR_NAME+result.getString("issued_by")+COLOR_INFO+" until "+result.getString("expires")+": "+result.getString("reason"));
		           					continue;
		           				}
		           				if (result.getString("type").equalsIgnoreCase("unban")) {
		                			respond(player, MSG_PREFIX+COLOR_NAME+result.getString("player_name")+COLOR_INFO+" unbanned by "+COLOR_NAME+result.getString("issued_by")+COLOR_INFO+": "+result.getString("reason"));
		           					continue;
		           				}
		           			}
		           			if (count == 0) {
	                			respond(player, MSG_PREFIX+COLOR_NAME+p_name+COLOR_INFO+" is not banned");
		           			}
		        		} catch (SQLException e) {
		        			e.printStackTrace();
		        			logger.warning(MSG_PREFIX+COLOR_WARNING+"SQL error: "+e.getLocalizedMessage());
		        		}
		       	        dbpool.releaseConnection(dbh);
	       	        }
	       	    }
        	}
        	return true;
        }
        return false;
    }
    
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
    	Player p = event.getPlayer();
    	if (p == null) return; // Should never happen
    	String p_name = p.getName();
    	String p_uuid = p.getUniqueId().toString();
    	String p_ip = event.getAddress().getHostAddress();

		logger.warning(MSG_PREFIX+COLOR_INFO+"Checking status of player "+COLOR_NAME+p_name+COLOR_INFO+" ("+p_ip+")");
		
    	String message = null;
		if (dbpool == null) {
			logger.info(MSG_PREFIX+COLOR_WARNING+"Retrying dbpool initialization...");
			initDbPool();
		}
   	    if (dbpool != null) { 
   	    	Connection dbh = dbpool.getConnection();
   	        if (dbh != null) {
   	        	message = banned(dbh, p_name, p_uuid, p_ip);
       	        dbpool.releaseConnection(dbh);
   	        }
   	    }
   	    if (message != null) {
			logger.warning(MSG_PREFIX+COLOR_WARNING+"Rejecting "+COLOR_NAME+p_name+COLOR_WARNING+": "+message);
   	    	event.setKickMessage(message);
   	    	event.setResult(Result.KICK_BANNED);
			return;
   	    }
		logger.warning(MSG_PREFIX+COLOR_INFO+"Cleared "+COLOR_NAME+p_name);
    }

    private void initDbPool() {
    	try {
	    	dbpool = new DbPool(
	    		settings.get("db_url"), 
	    		settings.get("db_user"), 
	    		settings.get("db_pass"),
	    		Integer.valueOf(settings.get("db_min")),
	    		Integer.valueOf(settings.get("db_max"))
	    	);
    	} catch (RuntimeException e) {
    		logger.warning(MSG_PREFIX+COLOR_WARNING+"Init error: "+e.getLocalizedMessage());
    	}
    }
    
    
    
    public boolean isDebugging(final Player player) {
        if (debugees.containsKey(player)) {
            return debugees.get(player);
        } else {
            return false;
        }
    }

    public void setDebugging(final Player player, final boolean value) {
        debugees.put(player, value);
    }
    
    // Code from author of Permissions.jar
    
    private void loadSettings() {
    	String fname = baseDir + "/" + configFile;
		String line = null;

		// Load the settings hash with defaults
		settings.put("db_url", "");
		settings.put("db_user", "");
		settings.put("db_pass", "");
		settings.put("db_min", "2");
		settings.put("db_max", "10");
		settings.put("enforce_name", "yes");
		settings.put("enforce_uuid", "yes");
		settings.put("enforce_ip", "no");
		// Read the current file (if it exists)
		try {
    		BufferedReader input =  new BufferedReader(new FileReader(fname));
    		while (( line = input.readLine()) != null) {
    			line = line.trim();
    			if (!line.startsWith("#") && line.contains("=")) {
    				String[] pair = line.split("=", 2);
    				settings.put(pair[0], pair[1]);
    			}
    		}
    		input.close();
    	}
    	catch (FileNotFoundException e) {
			logger.warning( MSG_PREFIX+COLOR_WARNING+"Error reading " + e.getLocalizedMessage() + ", using defaults" );
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    
    private void respond(Player player, String message) {
    	if (player == null) {
        	Server server = getServer();
        	ConsoleCommandSender console = server.getConsoleSender();
        	console.sendMessage(message);
    	} else {
    		player.sendMessage(message);
    	}
    }
    

    private boolean permBan(Connection dbh, String p_name, String p_uuid, String p_ip, String op, String reason) {
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "INSERT INTO bans (" +
	          "  type, player_name, player_uuid, player_ip, issued_by, reason" +
	          ") VALUES (" +
	          "  'permban', ?, ?, ?, ?, ?" +
	          ")"
	        );
   			logger.info(MSG_PREFIX+""+p_name+", "+p_uuid+", "+p_ip+", "+op+", "+reason);
   			sth.setNString(1, p_name);
   			sth.setNString(2, p_uuid);
   			sth.setNString(3, p_ip);
   			sth.setNString(4, op);
   			sth.setNString(5, reason);
   			sth.executeUpdate();
   			logger.info(MSG_PREFIX+COLOR_INFO+"Permanent: "+COLOR_NAME+p_name+COLOR_INFO+" issued by "+COLOR_NAME+op+COLOR_INFO+" ("+reason+")");
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"SQL error: "+e.getLocalizedMessage());
			return false;
		}
    	return true;
    }
    
    private boolean tempBan(Connection dbh, String p_name, String p_uuid, String p_ip, String op, String reason, String expires) {
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "INSERT INTO bans (" +
	          "  type, player_name, player_uuid, player_ip, issued_by, reason, expires" +
	          ") VALUES (" +
	          "  'tempban', ?, ?, ?, ?, ?, ?" +
	          ")"
	        );
   			logger.info(MSG_PREFIX+""+p_name+", "+p_uuid+", "+p_ip+", "+op+", "+reason+", "+expires);
   			sth.setNString(1, p_name);
   			sth.setNString(2, p_uuid);
   			sth.setNString(3, p_ip);
   			sth.setNString(4, op);
   			sth.setNString(5, reason);
   			sth.setNString(6, expires);
   			sth.executeUpdate();
   			logger.info(MSG_PREFIX+COLOR_INFO+"Temporary: "+COLOR_NAME+p_name+COLOR_INFO+" until "+expires+" issued by "+COLOR_NAME+op+COLOR_INFO+" ("+reason+")");
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"SQL error: "+e.getLocalizedMessage());
			return false;
		}
    	return true;
    }
    
    private boolean unBan(Connection dbh, String p_name, String p_uuid, String p_ip, String op, String reason) {
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "INSERT INTO bans (" +
	          "  type, player_name, player_uuid, player_ip, issued_by, reason" +
	          ") VALUES (" +
	          "  'unban', ?, ?, ?, ?, ?" +
	          ")"
	        );
   			sth.setNString(1, p_name);
   			sth.setNString(2, p_uuid);
   			sth.setNString(3, p_ip);
   			sth.setNString(4, op);
   			sth.setNString(5, reason);
   			sth.executeUpdate();
   			logger.info(MSG_PREFIX+COLOR_INFO+"Pardon: "+COLOR_NAME+p_name+COLOR_INFO+" issued by "+COLOR_NAME+op+COLOR_INFO+" ("+reason+")");
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"SQL error: "+e.getLocalizedMessage());
			return false;
		}
    	return true;
    }
    
    private String uuid_by_name(Connection dbh, String p_name) {
    	// Scan table 'logins' to see if we have a UUID to match player name
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "SELECT uuid FROM logins " +
	          "WHERE name = ? " +
	          "ORDER BY last_login DESC " +
	          "LIMIT 1"
	        );
	        sth.setNString(1, p_name);
   			ResultSet result = sth.executeQuery();
   			if (result.next()) {
   				return result.getString("uuid");
   			}
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"(uuid_by_name) SQL error: "+e.getLocalizedMessage());
		}
    	return "";
    }
    
    private String ip_by_name(Connection dbh, String p_name) {
    	// Scan table 'logins' to see if we have an IP to match player name
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "SELECT ip FROM logins " +
	          "WHERE name = ? " +
	          "ORDER BY last_login DESC " +
	          "LIMIT 1"
	        );
	        sth.setNString(1, p_name);
   			ResultSet result = sth.executeQuery();
   			if (result.next()) {
   				return result.getString("ip");
   			}
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"(uuid_by_name) SQL error: "+e.getLocalizedMessage());
		}
    	return "";
    }

    private String banned(Connection dbh, String p_name, String p_uuid, String p_ip) {
    	String message = null;
    	boolean permanent = false;
    	
    	// Record player/uuid/ip
    	try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "REPLACE INTO logins " +
	          "(name, uuid, ip)" +
	          "VALUES " +
	          "(?, ?, ?)"
	        );
	        sth.setNString(1, p_name);
	        sth.setNString(2, p_uuid);
	        sth.setNString(3, p_ip);
	        sth.execute();
	        
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"(trace) SQL error: "+e.getLocalizedMessage());
    	}
    	
    	// Update legacy records (this MAY be incorrect but it's the lesser of two evils)
    	try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "UPDATE bans " +
	          "SET player_uuid = ?, player_ip = ?" +
	          "WHERE player_name = ? " +
	          "AND (player_uuid IS NULL OR player_uuid = '') " +
	          "AND (player_ip IS NULL OR player_ip = '')"
	        );
	        sth.setNString(1, p_uuid);
	        sth.setNString(2, p_ip);
	        sth.setNString(3, p_name);
	        sth.execute();
	        
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"(update) SQL error: "+e.getLocalizedMessage());
    	}
    	
    	
    	// Look up player/uuid/ip
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(
	          "SELECT * FROM bans " +
	          "WHERE 0=1 " +
//	          (settings.get("enforce_name").equalsIgnoreCase("yes") ? "OR (player_name != '' AND player_name LIKE ?) " : "") +
	          (settings.get("enforce_uuid").equalsIgnoreCase("yes") ? "OR (player_uuid != '' AND player_uuid LIKE ?) " : "") +
	          (settings.get("enforce_ip").equalsIgnoreCase("yes")   ? "OR (player_ip != '' AND player_ip LIKE ?) "   : "") +
	          "AND (expires IS NULL OR expires < NOW()) " +
	          "ORDER BY issued"
	        );
	        Integer i = 1;
//	        if (settings.get("enforce_name").equalsIgnoreCase("yes")) {
//        	logger.info(MSG_PREFIX+"(check) Enforcing name bans");
//	        	sth.setNString(i, p_name);
//	        	i++;
//	        }
	        if (settings.get("enforce_uuid").equalsIgnoreCase("yes")) {
	        	logger.info(MSG_PREFIX+COLOR_INFO+"(check) Enforcing uuid bans");
	        	sth.setNString(i, p_uuid);
	        	i++;
	        }
	        if (settings.get("enforce_ip").equalsIgnoreCase("yes")) {
	        	logger.info(MSG_PREFIX+COLOR_INFO+"(check) Enforcing ip bans");
	        	sth.setNString(i, p_ip);
	        	i++;
	        }
   			ResultSet result = sth.executeQuery();
   			while (result.next()) {
   				logger.info(MSG_PREFIX+COLOR_INFO+"(check) found "+result.getString("type")+" for "+COLOR_NAME+result.getString("player_name")+COLOR_INFO+" uuid="+result.getString("player_uuid")+" ip="+result.getString("player_ip"));
   				if (result.getString("type").equalsIgnoreCase("permban")) {
   					message = "Banned by "+result.getString("issued_by")+": "+result.getString("reason");
   					permanent = true;
   					continue;
   				}
   				if (result.getString("type").equalsIgnoreCase("tempban") && permanent == false) {
   					message = "Banned by "+result.getString("issued_by")+" until "+result.getString("expires")+": "+result.getString("reason");
   					continue;
   				}
   				if (result.getString("type").equalsIgnoreCase("unban")) {
   					message = null;
   					permanent = false;
   					continue;
   				}
   			}
   			return message;
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning(MSG_PREFIX+COLOR_WARNING+"(check) SQL error: "+e.getLocalizedMessage());
		}
		
    	return null;
    }
}

