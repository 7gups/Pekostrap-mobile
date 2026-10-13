package com.voidstrap.android;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Typed calls for the pekora.pro apisite endpoints seen in the sampled responses. Account calls take the
 * session value, which is sent only as a Cookie header on that request and is never logged. Authenticated
 * calls do not follow redirects, so the cookie cannot be sent to another host. String arguments are
 * checked against a strict character set before they are placed in a URL.
 */
final class PekoraApi {
    static final String BASE = "https://pekora.pro/apisite/";
    private static final int TIMEOUT_MS = 15_000;
    private static final int LIMIT = 512_000;
    private static final String SAFE = "[A-Za-z0-9_,.:/=&-]*";

    private PekoraApi() {
    }

    static String get(String path, String session) {
        String[] headers = session == null
                ? new String[]{"Accept", "application/json", "User-Agent", "Voidstrap Android"}
                : new String[]{"Cookie", PekoraSession.COOKIE + "=" + session, "Accept", "application/json", "User-Agent", "Voidstrap Android"};
        Object[] res = Http.request("GET", BASE + path, headers, null, TIMEOUT_MS, LIMIT, session == null);
        if (res == null || ((int[]) res[0])[0] != 200) return null;
        return new String((byte[]) res[2], StandardCharsets.UTF_8);
    }

    /** POST a JSON body. Returns the response body, or null on any non-200 result. */
    static String post(String path, String json, String session) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String[] headers = session == null
                ? new String[]{"Content-Type", "application/json", "Accept", "application/json", "User-Agent", "Voidstrap Android"}
                : new String[]{"Cookie", PekoraSession.COOKIE + "=" + session, "Content-Type", "application/json", "Accept", "application/json", "User-Agent", "Voidstrap Android"};
        Object[] res = Http.request("POST", BASE + path, headers, body, TIMEOUT_MS, LIMIT, session == null);
        if (res == null || ((int[]) res[0])[0] != 200) return null;
        return new String((byte[]) res[2], StandardCharsets.UTF_8);
    }

    static JSONObject postObject(String path, String json, String session) {
        String body = post(path, json, session);
        if (body == null) return null;
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            return null;
        }
    }

    static JSONObject object(String path, String session) {
        String body = get(path, session);
        if (body == null) return null;
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            return null;
        }
    }

    static JSONArray array(String path, String session) {
        String body = get(path, session);
        if (body == null) return null;
        try {
            return new JSONArray(body);
        } catch (JSONException e) {
            return null;
        }
    }

    static int count(String path, String session) {
        JSONObject o = object(path, session);
        return o == null ? -1 : o.optInt("count", -1);
    }

    private static boolean safe(String v) {
        return v != null && v.matches(SAFE);
    }

    // ---- Account (session required)

    static JSONObject me(String session) {
        return object("users/v1/users/authenticated", session);
    }

    static JSONObject currency(long userId, String session) {
        return object("economy/v1/users/" + userId + "/currency", session);
    }

    static JSONArray friends(long userId, String session) {
        JSONObject o = object("friends/v1/users/" + userId + "/friends", session);
        return o == null ? null : o.optJSONArray("data");
    }

    static int friendRequests(String session) {
        return count("friends/v1/user/friend-requests/count", session);
    }

    static int tradesInbound(String session) {
        return count("trades/v1/trades/inbound/count", session);
    }

    static JSONObject tradesInboundPage(String cursor, String session) {
        return safe(cursor) ? object("trades/v1/trades/inbound?cursor=" + cursor, session) : null;
    }

    static int unreadMessages(String session) {
        return count("privatemessages/v1/messages/unread/count", session);
    }

    static JSONObject ownsAssets(long userId, String assetIds, String session) {
        return safe(assetIds) ? object("inventory/v1/users/" + userId + "/items/asset/owns-assets?assetIds=" + assetIds, session) : null;
    }

    // ---- Public

    static JSONObject alertInfo() {
        return object("api/alerts/alert-info", null);
    }

    static JSONObject userInfo(long userId) {
        return object("users/v1/users/" + userId, null);
    }

    static JSONObject headshots(String userIds, String size) {
        return safe(userIds) && safe(size) ? object("thumbnails/v1/users/avatar-headshot?userIds=" + userIds + "&size=" + size + "&format=png", null) : null;
    }

    static JSONObject navigationMenu() {
        return object("catalog/v3/navigation-menu-items", null);
    }

    static JSONObject catalogSearch(String query) {
        return safe(query) ? object("catalog/v3/search/items?" + query, null) : null;
    }

    static JSONObject gameSorts() {
        return object("games/v1/games/sorts?gameSortsContext=GamesDefaultSorts", null);
    }

    static JSONObject gameList(String sortToken, int maxRows) {
        return safe(sortToken) ? object("games/v1/games/list?sortToken=" + sortToken + "&maxRows=" + maxRows + "&genre=0&keyword=", null) : null;
    }

    static JSONArray gamesByUniverse(String universeIds) {
        JSONObject o = safe(universeIds) ? object("games/v1/games?universeIds=" + universeIds, null) : null;
        return o == null ? null : o.optJSONArray("data");
    }

    static JSONObject votes(String universeIds) {
        return safe(universeIds) ? object("games/v1/games/votes?universeIds=" + universeIds, null) : null;
    }

    static JSONObject recommendations(long universeId, int maxRows) {
        return object("games/v1/games/recommendations/game/" + universeId + "?maxRows=" + maxRows, null);
    }

    static JSONObject gamePasses(long universeId) {
        return object("games/v1/games/" + universeId + "/game-passes", null);
    }

    static JSONObject privateServerSettings(long universeId) {
        return object("games/v1/games/" + universeId + "/private-servers/settings", null);
    }

    static JSONObject privateServers(long universeId) {
        return object("games/v1/games/" + universeId + "/private-servers", null);
    }

    static JSONObject gameMedia(long universeId) {
        return object("games/v2/games/" + universeId + "/media", null);
    }

    static JSONObject badges(long universeId, int limit) {
        return object("badges/v1/universes/" + universeId + "/badges?limit=" + limit, null);
    }

    static JSONArray placeDetails(String placeIds) {
        return safe(placeIds) ? array("games/v1/games/multiget-place-details?placeIds=" + placeIds, null) : null;
    }

    static JSONObject gameIcons(String universeIds, String size) {
        return safe(universeIds) && safe(size) ? object("thumbnails/v1/games/icons?size=" + size + "&format=png&universeIds=" + universeIds, null) : null;
    }

    static JSONObject assetThumbs(String assetIds, String size) {
        return safe(assetIds) && safe(size) ? object("thumbnails/v1/assets?assetIds=" + assetIds + "&format=png&size=" + size, null) : null;
    }

    static JSONObject forumInfo(long subCategoryId) {
        return object("forums/v1/sub-category/" + subCategoryId + "/info", null);
    }

    static JSONObject forumPosts(long subCategoryId, int limit, String cursor) {
        return safe(cursor) ? object("forums/v1/sub-category/" + subCategoryId + "/posts?limit=" + limit + "&cursor=" + cursor, null) : null;
    }

    /** Presence for a list of user ids. Response: userPresences[] with userPresenceType, placeId, userId, lastOnline. */
    static JSONObject presence(long[] userIds) {
        if (userIds == null || userIds.length == 0 || userIds.length > 100) return null;
        StringBuilder sb = new StringBuilder("{\"userIds\":[");
        for (int i = 0; i < userIds.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(userIds[i]);
        }
        sb.append("]}");
        return postObject("presence/v1/presence/users", sb.toString(), null);
    }

    /** Catalog details for asset ids. Response: data[] with name, price, priceStatus, isForSale, owned, purchaseCount. */
    static JSONArray catalogItemDetails(long[] assetIds) {
        if (assetIds == null || assetIds.length == 0 || assetIds.length > 100) return null;
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < assetIds.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"itemType\":\"Asset\",\"id\":").append(assetIds[i]).append('}');
        }
        sb.append("]}");
        JSONObject o = postObject("catalog/v1/catalog/items/details", sb.toString(), null);
        return o == null ? null : o.optJSONArray("data");
    }

    /** Only pekora.pro links may be opened from API content. */
    static boolean safeLink(String url) {
        Uri u = Uri.parse(url == null ? "" : url);
        String host = u.getHost();
        return "https".equals(u.getScheme()) && host != null && (host.equals("pekora.pro") || host.endsWith(".pekora.pro"));
    }
}
