package me.x128.xInventoriesConverter;

import com.mojang.api.profiles.HttpProfileRepository;
import com.mojang.api.profiles.Profile;
import me.x128.xInventories.utils.FileManager;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

import org.json.simple.JSONObject;

// Yikes, my apologies for anyone trying to decipher what's going on here.
// This is incredibly poorly organized. 

public class Main extends JavaPlugin{
    int skipped = 0;
    @Override
    public void onEnable() {
        this.getLogger().info("Plugin enabled");

    }
    @Override
    public void onDisable() {
        this.getLogger().info("Plugin disabled");
    }

    public void execute(CommandSender sender) {
        skipped = 0;
        if (!sender.hasPermission("xinventoriesconverter.convert")) {
            sender.sendMessage("§cYou do not have permission to execute this command!");
        }

        sendMessage(sender, "Starting conversion wizard");

        //get the multiverse inventories groups folder. if it's not a folder, we abort
        File f = new File("plugins/Multiverse-Inventories/groups");
        if (!f.isDirectory()) {
            sendMessage(sender, "An internal error has occured. Aborting. (1001)");
        }

        //create a new array with the group names
        File[] listOfFiles = f.listFiles();
        ArrayList<String> groups = new ArrayList<String>();
        for (File file : listOfFiles) {
            if (file.isDirectory()) {
                groups.add(file.getName());
            }
        }

        //abort if no groups
        if (groups.isEmpty()) {
            sendMessage(sender, "An internal error has occured. Aborting. (1002)");
        }

        //send the sender a message with the groups we will convert
        String groupListMessage = "Converting groups: ";
        for (String s : groups) {
            groupListMessage += "§b" + s + "§6, ";
        }
        sendMessage(sender, groupListMessage.substring(0, groupListMessage.length() - 2));

        //start converting each group
        sendMessage(sender, "Starting conversion...");
        for (String group : groups) {
            sendMessage(sender, "Converting group §b" + group);
            File groupFolder = new File(f.getPath() + File.separator + group);

            File[] uncleanedGroupFiles = groupFolder.listFiles();
            ArrayList<String> playerArr = new ArrayList<String>();

            for (File f2 : uncleanedGroupFiles) {
                if (FilenameUtils.getExtension(f2.getPath()).equalsIgnoreCase("json")) {
                    String file = FilenameUtils.getName(f2.getPath());
                    playerArr.add(file.substring(0, file.length() - 5));
                }
            }

            HashMap<String, String> uuidMap = getOfflineUUIDByName(playerArr);

            for (Map.Entry<String, String> entry : uuidMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                convertPlayerJson(sender, key, value, groupFolder.getPath() + File.separator + key + ".json",
                        me.x128.xInventories.Main.getPlugin().getDataFolder() + File.separator + "groups" + File.separator + group + File.separator + value + ".yml");
                //getLogger().info("Processing " + key);

            }
        }

        //convertGroups(sender);

        if (skipped > 0) {
            sendMessage(sender, "Skipped " + skipped + " players with empty inventories");
        }
        sendMessage(sender, "Done! Conversion complete!\n\n");
        sendMessage(sender, "Next steps:");
        sendMessage(sender, "§b1. §6Stop the server");
        sendMessage(sender, "§b2. §6Delete §bMultiverse-Inventories §6& §bxInventories-Converter §6from your plugins folder");
        sendMessage(sender, "§b3. §6Configure xInventories with the newly converted groups\n");
        sendMessage(sender, "For more detailed instructions, check the §bxInventories-Converter §6homepage on SpigotMC");
    }

    public boolean onCommand(final CommandSender sender, Command cmd, String label, String[] args) {
        new BukkitRunnable() {

            @Override
            public void run() {
                // What you want to schedule goes here
                execute(sender);
            }

        }.runTaskAsynchronously(this);
        return true;
    }

    public void convertGroups(CommandSender sender) {
        //get the multiverse inventories worlds folder. if it's not a folder, we abort
        File f = new File("plugins/Multiverse-Inventories/worlds");
        if (!f.isDirectory()) {
            return;
        }

        //create a new array with the world names
        File[] listOfFiles = f.listFiles();
        ArrayList<String> groups = new ArrayList<String>();
        for (File file : listOfFiles) {
            if (file.isDirectory()) {
                groups.add(file.getName());
            }
        }

        //abort if no worlds
        if (groups.isEmpty()) {
            return;
        }

        //send the sender a message with the worlds we will convert
        String groupListMessage = "Converting worlds: ";
        for (String s : groups) {
            groupListMessage += "§b" + s + "§6, ";
        }
        sendMessage(sender, groupListMessage.substring(0, groupListMessage.length() - 2));

        //start converting each group
        for (String group : groups) {
            sendMessage(sender, "Converting world §b" + group);
            File groupFolder = new File(f.getPath() + File.separator + group);

            File[] uncleanedGroupFiles = groupFolder.listFiles();
            ArrayList<String> playerArr = new ArrayList<String>();

            for (File f2 : uncleanedGroupFiles) {
                if (FilenameUtils.getExtension(f2.getPath()).equalsIgnoreCase("json")) {
                    String file = FilenameUtils.getName(f2.getPath());
                    playerArr.add(file.substring(0, file.length() - 5));
                }
            }

            HashMap<String, String> uuidMap = getMojangUUIDByName(playerArr);

            for (Map.Entry<String, String> entry : uuidMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                convertPlayerJson(sender, key, value, groupFolder.getPath() + File.separator + key + ".json",
                        "plugins/Multiverse-Inventories/worlds_xinventories" + File.separator + group + File.separator + value + ".yml");

            }
        }
    }

    public void convertPlayerJson(CommandSender sender, String key, String value, String jsonPath, String savePath) {
        //RUN
        try {
            BufferedReader br = new BufferedReader(new FileReader(jsonPath));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
                //begin parsing json
                JSONParser parser = new JSONParser();

                String toParse = sb.toString();

                while (true) {
                    int ifIndex = toParse.indexOf("ItemFlags");
                    if (ifIndex > 0) {
                        String subs = toParse.substring(ifIndex);
                        int exitIndex = subs.indexOf("]");
                        if (exitIndex > 0) {
                            String totalSubs = toParse.substring(0, ifIndex - 1) +  subs.substring(exitIndex + 1);
                            toParse = totalSubs;
                        }
                    } else {
                        break;
                    }
                }

                Object obj = parser.parse(toParse);
                JSONObject jsonObject = (JSONObject) obj;
                JSONObject survivalObject = (JSONObject) jsonObject.get("SURVIVAL");
                if (survivalObject == null) {
                    getLogger().info("Skipping player " + key + ": no survival inventory");
                    return;
                }
                JSONObject inventoryJson = (JSONObject) survivalObject.get("inventoryContents");
                JSONObject enderJson = (JSONObject) survivalObject.get("enderChestContents");
                JSONObject armorJson = (JSONObject) survivalObject.get("armorContents");
                JSONObject statsJson = (JSONObject) survivalObject.get("stats");
                if (inventoryJson == null) {
                    skipped++;
                    break;
                }

                //inventorycontents
                Inventory inventoryContents = Bukkit.createInventory(null, 36);
                for (int i = 0; i < 36; i++) {
                    String stackData = "";
                    if (inventoryJson.get(i + "") != null) {
                        stackData = inventoryJson.get(i + "").toString();
                    }
                    ItemStack is = parseItem(sender, stackData, key);
                    inventoryContents.setItem(i, is);
                }

                //enderchest
                Inventory enderContents = Bukkit.createInventory(null, 27);
                if (enderJson != null) {
                    for (int i = 0; i < 27; i++) {
                        String stackData = "";
                        if (enderJson.get(i + "") != null) {
                            stackData = enderJson.get(i + "").toString();
                        }
                        ItemStack is = parseItem(sender, stackData, key);
                        enderContents.setItem(i, is);
                    }
                }

                //armor
                ItemStack helm = new ItemStack(Material.AIR);
                ItemStack chest = new ItemStack(Material.AIR);
                ItemStack leggings = new ItemStack(Material.AIR);
                ItemStack boots = new ItemStack(Material.AIR);
                if (armorJson != null) {
                    if (armorJson.get(0 + "") != null) {
                        boots = parseItem(sender, armorJson.get(0 + "").toString(), key);
                    }
                    if (armorJson.get(1 + "") != null) {
                        leggings = parseItem(sender, armorJson.get(1 + "").toString(), key);
                    }
                    if (armorJson.get(2 + "") != null) {
                        chest = parseItem(sender, armorJson.get(2 + "").toString(), key);
                    }
                    if (armorJson.get(3 + "") != null) {
                        helm = parseItem(sender, armorJson.get(3 + "").toString(), key);
                    }
                }

                int xpLevels = 0;
                if (armorJson != null) {
                    if (statsJson.get("el") != null) {
                        String el = statsJson.get("el").toString();
                        xpLevels = Integer.parseInt(el);
                    }
                }

                File fromFile = new File(savePath);
                FileConfiguration fromConfig = FileManager.getYaml(fromFile);

                int pos = 0;
                for (ItemStack is : inventoryContents) {
                    fromConfig.set("inventory." + pos, is);
                    pos ++;
                }

                pos = 0;
                for (ItemStack is : enderContents) {
                    fromConfig.set("ender_chest." + pos, is);
                    pos ++;
                }

                fromConfig.set("armor_contents.helmet", helm);
                fromConfig.set("armor_contents.chestplate", chest);
                fromConfig.set("armor_contents.leggings", leggings);
                fromConfig.set("armor_contents.boots", boots);

                fromConfig.set("exp-level", xpLevels);

                FileManager.saveConfiguraton(fromFile, fromConfig, true);

            }
        } catch (Exception e) {
            System.out.println(e);
            sendMessage(sender, "§cAn internal error has occured. Aborting. (1003) [" + key + "]");
            return;
        }
    }

    public ItemStack parseItem(CommandSender sender, String str, String name) {
        ItemStack is = new ItemStack(Material.AIR);
        if (str.isEmpty()) {
            return is;
        }
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(str);
            JSONObject jsonObject = (JSONObject)obj;
            if (jsonObject.get("type") != null) {
                String type = jsonObject.get("type").toString();
                is.setType(Material.valueOf(type));
            }
            if (jsonObject.get("amount") != null) {
                int amt = Integer.parseInt(jsonObject.get("amount").toString());
                is.setAmount(amt);
            }
            if (jsonObject.get("damage") != null) {
                int dmg = Integer.parseInt(jsonObject.get("damage").toString());
                is.setDurability((short) dmg);
            }
            if (jsonObject.get("meta") != null) {
                JSONObject metaObject = (JSONObject)jsonObject.get("meta");
                ItemMeta im = is.getItemMeta();
                if (metaObject.get("display-name") != null) {
                    String displyName = metaObject.get("display-name").toString();
                    im.setDisplayName(displyName);
                }
                if (metaObject.get("lore") != null) {
                    JSONArray ar = (JSONArray)metaObject.get("lore");
                    im.setLore(ar);
                }
                if (metaObject.get("enchants") != null) {
                    JSONObject enchants = (JSONObject)metaObject.get("enchants");
                    Iterator iter = enchants.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry entry = (Map.Entry)iter.next();
                        String enchant = entry.getKey().toString();
                        String valueString = entry.getValue().toString();
                        int value = Integer.parseInt(valueString);
                        Enchantment e = Enchantment.getByName(enchant);
                        if (e != null) {
                            im.addEnchant(e, value, true);
                        } else {
                            sendMessage(sender, "Error parsing enchantment for §b" + is.getType().toString() + " §6in §b" + name + "'s inventory");
                        }
                    }
                }
                is.setItemMeta(im);

                //handle enchanted books seperately because bukkit handles them differently for some stupid reason
                if (metaObject.get("stored-enchants") != null) {
                    if (is.getType().equals(Material.ENCHANTED_BOOK)) {
                        JSONObject enchants = (JSONObject)metaObject.get("stored-enchants");
                        Iterator iter = enchants.entrySet().iterator();
                        while (iter.hasNext()) {
                            Map.Entry entry = (Map.Entry) iter.next();
                            String enchant = entry.getKey().toString();
                            String valueString = entry.getValue().toString();
                            int value = Integer.parseInt(valueString);
                            EnchantmentStorageMeta meta = (EnchantmentStorageMeta)is.getItemMeta();
                            meta.addStoredEnchant(Enchantment.getByName(enchant), value, true);
                            is.setItemMeta(meta);
                        }
                    }
                }
            }
            //String damageString = jsonObject.get("type").toString();


        } catch (Exception e) {
            sendMessage(sender, "An internal error has occured while parsing an item. Continuing. (1004)");
            sender.sendMessage(e.getMessage());
        }
        return is;
    }

    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage("§3[xInventories] §6" + message);
    }

    public  HashMap<String, String> getMojangUUIDByName(List<String> names) {
        getLogger().info("Requested " + names.size() + " UUID conversions from Mojang");
        HttpProfileRepository repository = new HttpProfileRepository("minecraft");
        String[] nameArray = names.toArray(new String[names.size()]);
        Profile[] profiles = repository.findProfilesByNames(nameArray);
        HashMap<String, String> hm = new HashMap<String, String>();
        for (Profile p : profiles) {
            String u = p.getId();
            u = insertDashUUID(u);
            hm.put(p.getName(), u);
            List<String> emp = new ArrayList<String>();
        }
        getLogger().info("Mojang returned " + hm.size() + " UUID conversions");
        return hm;
    }

    public  HashMap<String, String> getOfflineUUIDByName(List<String> names) {
        int total = names.size();
        int current = 1;
        HashMap<String, String> hm = new HashMap<String, String>();
        for (String name : names) {
            hm.put(name, Bukkit.getOfflinePlayer(name).getUniqueId().toString());
            getLogger().info("- Converted " + current + " of " + total + " in current group");
            current++;
        }
        return hm;
    }

    public static String insertDashUUID(String uuid) {
        StringBuffer sb = new StringBuffer(uuid);
        sb.insert(8, "-");

        sb = new StringBuffer(sb.toString());
        sb.insert(13, "-");

        sb = new StringBuffer(sb.toString());
        sb.insert(18, "-");

        sb = new StringBuffer(sb.toString());
        sb.insert(23, "-");

        return sb.toString();
    }

}
