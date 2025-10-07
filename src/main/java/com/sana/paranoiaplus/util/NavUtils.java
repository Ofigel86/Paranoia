package com.sana.paranoiaplus.util;

import org.bukkit.Location;
import java.util.*;

/**
 * NavUtils - skeleton A* pathfinding utilities. Real implementation should be async and optimized.
 * This file provides structure for path requests and a simple node class.
 */
public final class NavUtils {
    private NavUtils() {}

    public static class Node {
        public final int x, y, z;
        public Node(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
    }

    public static List<Location> findPath(Location start, Location goal, int maxNodes) {
        // Placeholder: real A* implementation required. Return empty list for now.
        return Collections.emptyList();
    }
}
