package me.colinstudios.lobbygames.cookie;

import java.util.UUID;

public record CookieLeaderboardEntry(int rank, UUID uuid, String name, long cookies) {
}
