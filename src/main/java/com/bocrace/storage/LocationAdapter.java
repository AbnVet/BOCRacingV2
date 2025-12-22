package com.bocrace.storage;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Type;

/**
 * Gson adapter for Bukkit Location serialization
 */
public class LocationAdapter implements JsonSerializer<Location>, JsonDeserializer<Location> {
    
    @Override
    public JsonElement serialize(Location location, Type type, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", location.getWorld().getName());
        obj.addProperty("x", location.getX());
        obj.addProperty("y", location.getY());
        obj.addProperty("z", location.getZ());
        obj.addProperty("yaw", location.getYaw());
        obj.addProperty("pitch", location.getPitch());
        return obj;
    }
    
    @Override
    public Location deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String worldName = obj.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new JsonParseException("World not found: " + worldName);
        }
        double x = obj.get("x").getAsDouble();
        double y = obj.get("y").getAsDouble();
        double z = obj.get("z").getAsDouble();
        float yaw = obj.get("yaw").getAsFloat();
        float pitch = obj.get("pitch").getAsFloat();
        return new Location(world, x, y, z, yaw, pitch);
    }
}
