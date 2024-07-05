package redltnt.redl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import redltnt.redl.commands.reloadCommand;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static redltnt.redl.Utils.color;

public final class RedLTnt extends JavaPlugin implements Listener {

    private Connection connection;
    FileConfiguration config = getConfig();
    private Map<UUID, String> tntData = new HashMap<>();

    @Override
    public void onEnable() {
        // Load and save the default configuration
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Create custom TNT recipes from config
        createCustomTntRecipes(config);

        // Register the command executor
        this.getCommand("rltnt").setExecutor(new reloadCommand(this));
        this.getCommand("rltnt").setTabCompleter(new Tab(this));

        String url = config.getString("database.url", "");
        String user = config.getString("database.user", "");
        String password = config.getString("database.password", "");
        String key = config.getString("key");

        try {
            connection = DriverManager.getConnection(url, user, password);
            if (!isValidKey(key)) {
                getLogger().warning("Invalid key! Disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Database connection error! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void createCustomTntRecipes(FileConfiguration config) {
        String trueTranslation = config.getString("translations.true", "&c&lДа");
        String falseTranslation = config.getString("translations.false", "&c&lНет");

        Set<String> keys = config.getConfigurationSection("config").getKeys(false);
        for (String key : keys) {
            String name = config.getString("config." + key + ".name");
            int fuseTick = config.getInt("config." + key + ".fuseTick");
            boolean isIncendiary = config.getBoolean("config." + key + ".isIncendiary");
            boolean lightning = config.getBoolean("config." + key + ".lightning");
            boolean breakObsidian = config.getBoolean("config." + key + ".breakObsidian");
            boolean breakBedrock = config.getBoolean("config." + key + ".breakBedrock");
            int setPower = config.getInt("config." + key + ".setPower", 4);
            String[] shape = new String[]{
                    config.getString("config." + key + ".craft.1"),
                    config.getString("config." + key + ".craft.2"),
                    config.getString("config." + key + ".craft.3")
            };
            Map<String, Object> ingredients = config.getConfigurationSection("config." + key + ".craft").getValues(false);

            // Create the custom TNT item stack with NBT tags
            ItemStack customTnt = new ItemStack(Material.TNT);
            ItemMeta meta = customTnt.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(name));

                // Set NBT tags for identification
                NamespacedKey keyTag = new NamespacedKey(this, "redl_key");
                meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, key);

                // Set the lore with placeholders replaced
                List<String> loreConfig = config.getStringList("config." + key + ".lore");
                List<String> lore = new ArrayList<>();
                for (String line : loreConfig) {
                    line = line.replace("%name%", name)
                            .replace("%fusetick%", String.valueOf(fuseTick))
                            .replace("%isincendiary%", isIncendiary ? trueTranslation : falseTranslation)
                            .replace("%power%", String.valueOf(setPower))
                            .replace("%lightning%", lightning ? trueTranslation : falseTranslation)
                            .replace("%breakObsidian%", breakObsidian ? trueTranslation : falseTranslation)
                            .replace("%breakBedrock%", breakBedrock ? trueTranslation : falseTranslation);
                    lore.add(color(line));
                }
                meta.setLore(lore);

                customTnt.setItemMeta(meta);
            }

            // Define the crafting recipe
            NamespacedKey namespacedKey = new NamespacedKey(this, "custom_tnt_" + key);
            ShapedRecipe recipe = new ShapedRecipe(namespacedKey, customTnt);
            recipe.shape(shape[0], shape[1], shape[2]);

            for (Map.Entry<String, Object> entry : ingredients.entrySet()) {
                if (entry.getKey().matches("[A-Z]")) {
                    Material material = Material.getMaterial(entry.getValue().toString());
                    if (material != null) {
                        recipe.setIngredient(entry.getKey().charAt(0), material);
                    }
                }
            }

            // Add the recipe to the server
            Bukkit.addRecipe(recipe);
        }
    }

    public void reloadRecipes() {
        try {
            // Remove existing custom TNT recipes
            Iterator<Recipe> it = Bukkit.recipeIterator();
            List<NamespacedKey> recipesToRemove = new ArrayList<>();
            while (it.hasNext()) {
                Recipe recipe = it.next();
                if (recipe instanceof ShapedRecipe && ((ShapedRecipe) recipe).getKey().getNamespace().equals(this.getName().toLowerCase())) {
                    recipesToRemove.add(((ShapedRecipe) recipe).getKey());
                }
            }
            for (NamespacedKey key : recipesToRemove) {
                Bukkit.removeRecipe(key);
            }
            // Reload the configuration and recreate the recipes
            createCustomTntRecipes(getConfig());
        } catch (Exception e) {
            getLogger().severe("An error occurred while reloading recipes.");
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Block block = e.getBlock();

        // Check if the placed block is TNT
        if (block.getType() == Material.TNT) {
            ItemStack itemInHand = e.getItemInHand();
            if (itemInHand.hasItemMeta()) {
                ItemMeta meta = itemInHand.getItemMeta();
                if (meta != null) {
                    String key = null;
                    NamespacedKey keyTag = new NamespacedKey(this, "redl_key");
                    if (meta.getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) {
                        key = meta.getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
                    }
                    if (key != null) {
                        block.setType(Material.AIR);
                        spawnCustomTNT(block.getLocation(), key);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getEntityType() == EntityType.PRIMED_TNT && tntData.containsKey(e.getEntity().getUniqueId())) {
            String key = tntData.get(e.getEntity().getUniqueId());
            FileConfiguration config = getConfig();

            boolean breakObsidian = config.getBoolean("config." + key + ".breakObsidian");
            boolean breakBedrock = config.getBoolean("config." + key + ".breakBedrock");

            Iterator<Block> iterator = e.blockList().iterator();
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getType() == Material.OBSIDIAN && !breakObsidian) {
                    iterator.remove();
                } else if (block.getType() == Material.BEDROCK && !breakBedrock) {
                    iterator.remove(); // Prevent bedrock from breaking
                } else if (block.getType() == Material.BEDROCK && breakBedrock) {
                    block.setType(Material.AIR); // Break bedrock if configured to do so
                }
            }
        }
    }

    public ItemStack getCustomTntItem(String key) {
        FileConfiguration config = getConfig();
        String trueTranslation = config.getString("translations.true", "&c&lДа");
        String falseTranslation = config.getString("translations.false", "&c&lНет");

        if (!config.contains("config." + key)) {
            return null; // Key does not exist in the configuration
        }

        String name = config.getString("config." + key + ".name");
        int fuseTick = config.getInt("config." + key + ".fuseTick");
        boolean isIncendiary = config.getBoolean("config." + key + ".isIncendiary");
        int setPower = config.getInt("config." + key + ".setPower", 4);
        boolean lightning = config.getBoolean("config." + key + ".lightning");
        boolean breakObsidian = config.getBoolean("config." + key + ".breakObsidian");
        boolean breakBedrock = config.getBoolean("config." + key + ".breakBedrock");

        ItemStack customTnt = new ItemStack(Material.TNT);
        ItemMeta meta = customTnt.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));

            // Set custom metadata to identify the TNT
            meta.getPersistentDataContainer().set(new NamespacedKey(this, "redl_key"), PersistentDataType.STRING, key);

            // Set lore with custom attributes from the configuration
            List<String> loreConfig = config.getStringList("config." + key + ".lore");
            List<String> lore = new ArrayList<>();
            for (String line : loreConfig) {
                line = line.replace("%name%", name)
                        .replace("%fusetick%", String.valueOf(fuseTick))
                        .replace("%isincendiary%", isIncendiary ? trueTranslation : falseTranslation)
                        .replace("%power%", String.valueOf(setPower))
                        .replace("%lightning%", lightning ? trueTranslation : falseTranslation)
                        .replace("%breakObsidian%", breakObsidian ? trueTranslation : falseTranslation)
                        .replace("%breakBedrock%", breakBedrock ? trueTranslation : falseTranslation);
                lore.add(color(line));
            }
            meta.setLore(lore);

            customTnt.setItemMeta(meta);
        }

        return customTnt;
    }

    private void spawnCustomTNT(Location location, String key) {
        FileConfiguration config = getConfig();
        int fuseTick = config.getInt("config." + key + ".fuseTick") * 20;
        boolean isIncendiary = config.getBoolean("config." + key + ".isIncendiary");
        boolean lightning = config.getBoolean("config." + key + ".lightning");
        boolean breakObsidian = config.getBoolean("config." + key + ".breakObsidian");
        boolean breakBedrock = config.getBoolean("config." + key + ".breakBedrock");
        int setPower = config.getInt("config." + key + ".setPower", 4); // Default power is 4 if not specified

        TNTPrimed tntPrimed = location.getWorld().spawn(location, TNTPrimed.class);
        tntPrimed.setCustomNameVisible(true);
        tntPrimed.setFuseTicks(fuseTick);
        tntPrimed.setIsIncendiary(isIncendiary);
        tntPrimed.setYield(setPower);
        tntPrimed.getPersistentDataContainer().set(new NamespacedKey(this, "redl_key"), PersistentDataType.STRING, key);
        tntData.put(tntPrimed.getUniqueId(), key);

        int a = fuseTick / 20;
        for (int i = 0; i < a; i++) {
            int finalA = a - i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (finalA <= 0) {
                    tntPrimed.remove();
                    return;
                }
                String updatedCountdownText = color(config.getString("messages.tntTimer").replace("%timer%", String.valueOf(finalA)));
                tntPrimed.setCustomName(updatedCountdownText);
            }, i * 20L);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            location.getWorld().strikeLightningEffect(location);
        }, fuseTick);
    }

    private boolean isValidKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String query = "SELECT * FROM `keyTnt` WHERE `key2` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}