package com.petalix.guidefirst;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Xml;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;

final class Panel {
    private static final String HOST = "petalix-control.caroliinaconsultant.chatgpt.site";
    private static final String ALIAS = "petalix.guide.activation";
    private final SharedPreferences saved;
    private final Context context;

    Panel(Context context) {
        this.context = context.getApplicationContext();
        saved = context.getSharedPreferences("guide_account", Context.MODE_PRIVATE);
    }

    boolean activated() { return saved.contains("session"); }

    JSONObject activate(String pin) throws Exception {
        JSONObject response = call("activate", pin, null);
        String token = response.optString("token");
        if (token.isEmpty()) throw new IOException("Petalix Control did not return an activation.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        if (!saved.edit().putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit()) {
            throw new IOException("The activation could not be saved.");
        }
        return response;
    }

    JSONObject status() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(saved.getString("iv", ""), Base64.DEFAULT)));
        String token = new String(cipher.doFinal(Base64.decode(saved.getString("session", ""), Base64.DEFAULT)), StandardCharsets.UTF_8);
        return call("status", null, token);
    }

    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (javax.crypto.SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private JSONObject call(String action, String pin, String token) throws Exception {
        String device = saved.getString("device", "");
        if (device.isEmpty()) {
            device = UUID.randomUUID().toString();
            saved.edit().putString("device", device).apply();
        }
        JSONObject request = new JSONObject().put("deviceId", device).put("brand", "petalix")
                .put("label", Build.MANUFACTURER + " " + Build.MODEL + " (Guide Preview)")
                .put("packageName", context.getPackageName()).put("clientVersion", "0.2")
                .put("clientBuild", 2).put("capabilities", new JSONArray().put("panel-session-origin-v1"));
        if (pin != null) request.put("code", pin);
        String route = "/api/device/" + action;
        URL endpoint = new URL("https://" + HOST + route);
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection connection = open(endpoint.toString());
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            try {
                try (OutputStream body = connection.getOutputStream()) {
                    body.write(request.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    URL next = new URL(endpoint, connection.getHeaderField("Location"));
                    String host = next.getHost();
                    if (!"https".equals(next.getProtocol()) ||
                            (next.getPort() != -1 && next.getPort() != 443) ||
                            next.getUserInfo() != null || next.getQuery() != null || next.getRef() != null ||
                            !route.equals(next.getPath()) ||
                            !(HOST.equals(host) || "petalix-control.pinkgiant5.chatgpt.site".equals(host))) {
                        throw new IOException("Petalix Control returned an unrecognized address.");
                    }
                    endpoint = next;
                    continue;
                }
                if (code < 200 || code >= 300) {
                    if (code == 401 || code == 403) {
                        if (pin == null) saved.edit().remove("session").remove("iv").apply();
                        throw new IOException(pin != null ? "This PIN could not activate the preview. Check your PIN and device limit."
                                : "This device needs a valid activation PIN.");
                    }
                    throw new IOException("Petalix Control is unavailable (" + code + ").");
                }
                try (InputStream in = connection.getInputStream()) { return new JSONObject(read(in)); }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Petalix Control redirected too many times.");
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        return connection;
    }

    private static String read(InputStream in) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            for (int count; (count = in.read(buffer)) != -1;) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String query(String base, String script, String username, String password) {
        return base + "/" + script + "?username=" + Uri.encode(username) + "&password=" + Uri.encode(password);
    }

    List<Channel> channels(JSONObject doc) throws Exception {
        String expires = doc.optString("expires");
        if (!expires.isEmpty() && Instant.parse(expires).toEpochMilli() <= System.currentTimeMillis()) {
            throw new IOException("This Petalix subscription has expired.");
        }
        String server = doc.optString("server").trim();
        if (!server.contains("://")) server = "http://" + server;
        Uri url = Uri.parse(server);
        String username = doc.optString("username"), password = doc.optString("password");
        if (!("https".equals(url.getScheme()) || "http".equals(url.getScheme())) ||
                url.getHost() == null || url.getUserInfo() != null || username.isEmpty() || password.isEmpty()) {
            throw new IOException("Petalix Control has no live TV provider assigned to this account.");
        }
        String base = url.getScheme() + "://" + url.getEncodedAuthority();
        String api = query(base, "player_api.php", username, password);
        try {
            Map<String, String> groups = new HashMap<>();
            JSONArray categories = array(api + "&action=get_live_categories");
            for (int i = 0; i < categories.length(); i++) {
                JSONObject item = categories.getJSONObject(i);
                groups.put(item.optString("category_id"), item.optString("category_name"));
            }
            JSONArray streams = array(api + "&action=get_live_streams");
            List<Channel> channels = new ArrayList<>();
            for (int i = 0; i < streams.length(); i++) {
                JSONObject item = streams.getJSONObject(i);
                String id = item.optString("stream_id");
                if (id.isEmpty() || item.optString("name").isEmpty()) continue;
                channels.add(new Channel(id, item.getString("name"),
                        groups.getOrDefault(item.optString("category_id"), "Uncategorized"),
                        item.isNull("epg_channel_id") ? "" : item.optString("epg_channel_id"),
                        base + "/live/" + Uri.encode(username) + "/" + Uri.encode(password) + "/" + Uri.encode(id) + ".ts"));
            }
            if (!channels.isEmpty()) return channels;
        } catch (IOException | JSONException ignored) {
            // Some providers serve the M3U endpoint without exposing the Xtream JSON API.
        }
        return m3u(query(base, "get.php", username, password) + "&type=m3u_plus&output=ts");
    }

    private static JSONArray array(String address) throws IOException, JSONException {
        HttpURLConnection connection = open(address);
        try (InputStream in = connection.getInputStream()) { return new JSONArray(read(in)); }
        finally { connection.disconnect(); }
    }

    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");

    private static List<Channel> m3u(String address) throws IOException {
        HttpURLConnection connection = open(address);
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            List<Channel> channels = new ArrayList<>();
            String name = "", group = "", epg = "";
            for (String line; (line = lines.readLine()) != null;) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) {
                    Matcher attrs = ATTRIBUTE.matcher(line);
                    int last = 0;
                    group = "Uncategorized";
                    epg = "";
                    while (attrs.find()) {
                        last = attrs.end();
                        if ("group-title".equals(attrs.group(1))) group = attrs.group(2);
                        if ("tvg-id".equals(attrs.group(1))) epg = attrs.group(2);
                    }
                    int comma = line.indexOf(',', last);
                    name = comma < 0 ? "" : line.substring(comma + 1).trim();
                } else if (line.startsWith("https://") || line.startsWith("http://")) {
                    if (!name.isEmpty() && !line.matches("(?i)https?://[^/]+/(?:movie|series)/.*")) {
                        channels.add(new Channel(line, name, group, epg, line));
                    }
                    name = "";
                }
            }
            if (channels.isEmpty()) throw new IOException("The assigned provider did not return live channels.");
            return channels;
        } finally { connection.disconnect(); }
    }

    Map<String, List<Channel.Program>> listings(JSONObject doc, List<Channel> channels) throws Exception {
        String server = doc.optString("server").trim();
        Uri url = Uri.parse(server.contains("://") ? server : "http://" + server);
        String address = query(url.getScheme() + "://" + url.getEncodedAuthority(), "xmltv.php",
                doc.optString("username"), doc.optString("password"));
        Set<String> ids = new HashSet<>();
        for (Channel channel : channels) if (!channel.epg.isEmpty()) ids.add(channel.epg);
        Map<String, List<Channel.Program>> schedule = new HashMap<>();
        if (ids.isEmpty()) return schedule;
        long now = System.currentTimeMillis(), earliest = now - 10800000, latest = now + 172800000;
        SimpleDateFormat standard = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        HttpURLConnection connection = open(address);
        try (PushbackInputStream body = new PushbackInputStream(new BufferedInputStream(connection.getInputStream()), 2)) {
            byte[] magic = new byte[2];
            int count = body.read(magic);
            if (count > 0) body.unread(magic, 0, count);
            InputStream stream = count == 2 && (magic[0] & 255) == 31 && (magic[1] & 255) == 139 ? new GZIPInputStream(body) : body;
            XmlPullParser xml = Xml.newPullParser();
            xml.setInput(stream, null);
            String id = "", title = "", desc = "";
            long start = 0, end = 0;
            for (int event; (event = xml.next()) != XmlPullParser.END_DOCUMENT;) {
                if (event == XmlPullParser.START_TAG && "programme".equals(xml.getName())) {
                    id = xml.getAttributeValue(null, "channel");
                    title = ""; desc = "";
                    if (!ids.contains(id)) { id = ""; continue; }
                    try {
                        String from = xml.getAttributeValue(null, "start"), to = xml.getAttributeValue(null, "stop");
                        start = standard.parse(from.length() == 14 ? from + " +0000" : from).getTime();
                        end = standard.parse(to.length() == 14 ? to + " +0000" : to).getTime();
                        if (end <= earliest || start >= latest || end <= start) id = "";
                    } catch (Exception e) { id = ""; }
                } else if (event == XmlPullParser.START_TAG && id != null && !id.isEmpty()) {
                    if ("title".equals(xml.getName()) && title.isEmpty()) title = xml.nextText();
                    if ("desc".equals(xml.getName()) && desc.isEmpty()) desc = xml.nextText();
                } else if (event == XmlPullParser.END_TAG && "programme".equals(xml.getName()) && id != null && !id.isEmpty()) {
                    if (!title.isEmpty()) schedule.computeIfAbsent(id, ignored -> new ArrayList<>())
                            .add(new Channel.Program(start, end, title, desc));
                    id = "";
                }
            }
        } finally { connection.disconnect(); }
        for (List<Channel.Program> shows : schedule.values()) shows.sort(Comparator.comparingLong(program -> program.start));
        return schedule;
    }

    Set<String> localCalls(String zip) throws Exception {
        JSONObject place;
        HttpURLConnection connection = open("https://api.zippopotam.us/us/" + zip);
        try (InputStream in = connection.getInputStream()) {
            place = new JSONObject(read(in)).getJSONArray("places").getJSONObject(0);
        } finally { connection.disconnect(); }
        double lat = Math.toRadians(place.getDouble("latitude"));
        double lon = Math.toRadians(place.getDouble("longitude"));
        JSONArray stations;
        try (InputStream in = context.getAssets().open("local-stations.json")) {
            stations = new JSONObject(read(in)).getJSONArray("stations");
        }
        Set<String> calls = new HashSet<>();
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.getJSONObject(i);
            double y = Math.toRadians(station.getDouble("lat"));
            double x = Math.toRadians(station.getDouble("lon"));
            double distance = Math.acos(Math.max(-1, Math.min(1, Math.sin(lat) * Math.sin(y) +
                    Math.cos(lat) * Math.cos(y) * Math.cos(lon - x)))) * 3959;
            if (distance <= 90) calls.add(station.getString("call").toUpperCase(Locale.US).replaceFirst("-(TV|DT)$", ""));
        }
        return calls;
    }
}
