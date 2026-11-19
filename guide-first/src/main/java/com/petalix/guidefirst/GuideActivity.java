package com.petalix.guidefirst;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class GuideActivity extends Activity {
    private static final int INK = 0xff101316, TEXT = 0xfff4eee3, MUTED = 0xffbdc2c8;
    private static final int GOLD = 0xffe5ba71, CELL = 0xff20262e, SELECTED = 0xff533e27;
    private static final long SLOT = 30 * 60 * 1000L;
    private static final long DAY = 48 * SLOT;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<Channel> channels = new ArrayList<>();
    private List<Channel> visible = new ArrayList<>();
    private Set<String> localCalls = Collections.emptySet();
    private String section = "Guide";
    private String category = "All channels";
    private boolean demo = true, full, loading;
    private int screenWidth, screenHeight, row, channelWidth, timelineWidth, artId;
    private long start, cursor;
    private Panel panel;
    private Channel tuned;
    private ExoPlayer player;
    private PlayerView video;
    private LinearLayout root, navigation, body, guide, ruler, details, commands;
    private FrameLayout hero;
    private HorizontalScrollView sportsShelf;
    private ImageView backdrop;
    private ListView grid;
    private GridAdapter adapter;
    private TextView sourceLabel, heading, metadata, summary, hint, blank;
    private final DateFormat clock = DateFormat.getTimeInstance(DateFormat.SHORT);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        panel = new Panel(this);
        cursor = System.currentTimeMillis();
        start = cursor - Math.floorMod(cursor, SLOT);
        channels = exampleChannels(start);
        setContentView(createScreen());
        immerse();
        populate();
        grid.post(() -> grid.requestFocus());
        if (panel.activated()) connect(null);
    }

    private void immerse() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onStart() {
        super.onStart();
        player = new ExoPlayer.Builder(this).build();
        video.setPlayer(player);
        if (tuned != null) play(tuned);
    }

    @Override protected void onStop() {
        video.setPlayer(null);
        if (player != null) player.release();
        player = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout createScreen() {
        root = column();
        root.setBackgroundColor(INK);
        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(20), 0, dp(14), 0);
        TextView brand = label("PETALIX", 17, GOLD, true);
        brand.setPadding(0, 0, dp(12), 0);
        navigation.addView(brand);
        navigation.addView(button("Guide", () -> show("Guide")));
        navigation.addView(button("Sports", () -> show("Sports")));
        navigation.addView(button("Services", () -> show("Services")));
        navigation.addView(button("Account", this::account));
        HorizontalScrollView nav = new HorizontalScrollView(this);
        nav.setHorizontalScrollBarEnabled(false);
        nav.addView(navigation);
        nav.setFillViewport(true);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(49)));

        body = column();
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        hero = new FrameLayout(this);
        body.addView(hero);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xff101316, 0xf0101316, 0x60101316, 0x20101316}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        details = column();
        details.setPadding(dp(24), dp(8), dp(18), dp(8));
        details.setGravity(Gravity.CENTER_VERTICAL);
        sourceLabel = label("", 12, GOLD, true);
        heading = label("", 28, TEXT, true);
        heading.setMaxLines(1);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        metadata = label("", 13, GOLD, true);
        summary = label("", 14, TEXT, false);
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(sourceLabel);
        details.addView(heading);
        details.addView(metadata);
        details.addView(summary);
        hero.addView(details, new FrameLayout.LayoutParams(-1, -1));
        video = new PlayerView(this);
        video.setUseController(false);
        video.setFocusable(false);
        video.setErrorMessageProvider(error -> android.util.Pair.create(error.errorCode,
                "Channel unavailable. Choose another channel or press OK to retry."));
        hero.addView(video);
        video.setVisibility(View.GONE);

        guide = column();
        body.addView(guide, new LinearLayout.LayoutParams(-1, 0, 1));
        commands = new LinearLayout(this);
        commands.setGravity(Gravity.CENTER_VERTICAL);
        commands.setPadding(dp(12), 0, dp(12), 0);
        commands.addView(button("Categories", this::categories));
        commands.addView(button("Now", this::now));
        hint = label("", 12, MUTED, false);
        commands.addView(hint, new LinearLayout.LayoutParams(0, -1, 1));
        guide.addView(commands, new LinearLayout.LayoutParams(-1, dp(39)));
        sportsShelf = new HorizontalScrollView(this);
        sportsShelf.setHorizontalScrollBarEnabled(false);
        sportsShelf.setVisibility(View.GONE);
        guide.addView(sportsShelf, new LinearLayout.LayoutParams(-1, dp(63)));
        ruler = new LinearLayout(this);
        ruler.setBackgroundColor(0xff191e23);
        guide.addView(ruler, new LinearLayout.LayoutParams(-1, dp(31)));

        FrameLayout rows = new FrameLayout(this);
        guide.addView(rows, new LinearLayout.LayoutParams(-1, 0, 1));
        grid = new ListView(this);
        grid.setDivider(null);
        grid.setSelector(android.R.color.transparent);
        grid.setFocusable(true);
        adapter = new GridAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            row = position;
            cursor = System.currentTimeMillis();
            selectChannel();
        });
        grid.setOnFocusChangeListener((v, focus) -> grid.post(adapter::notifyDataSetChanged));
        rows.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        blank = label("", 16, MUTED, false);
        blank.setGravity(Gravity.CENTER);
        rows.addView(blank, new FrameLayout.LayoutParams(-1, -1));
        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l == screenWidth && b - t == screenHeight) return;
            screenWidth = r - l;
            screenHeight = b - t;
            layout();
        });
        return root;
    }

    private void layout() {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        boolean compact = screenWidth < dp(640);
        boolean preview = tuned != null && !section.equals("Services");
        root.getChildAt(0).setVisibility(full ? View.GONE : View.VISIBLE);
        int heroHeight = full ? screenHeight : Math.min(screenHeight / (compact && preview ? 2 : 3), dp(280));
        hero.setLayoutParams(new LinearLayout.LayoutParams(-1, heroHeight));
        details.setVisibility(full ? View.GONE : View.VISIBLE);
        int previewWidth = full || compact ? screenWidth : screenWidth * 44 / 100;
        int previewHeight = full ? heroHeight : compact ? heroHeight * 54 / 100 : heroHeight;
        video.setLayoutParams(new FrameLayout.LayoutParams(previewWidth, previewHeight, Gravity.END | Gravity.TOP));
        video.setVisibility(preview ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams info = new FrameLayout.LayoutParams(preview && !compact ? screenWidth - previewWidth : -1,
                preview && compact ? heroHeight - previewHeight : -1, Gravity.BOTTOM | Gravity.START);
        details.setLayoutParams(info);
        heading.setTextSize(compact ? 20 : 28);
        guide.setVisibility(full ? View.GONE : View.VISIBLE);
        channelWidth = Math.min(dp(235), Math.max(dp(86), screenWidth / 5));
        timelineWidth = Math.max(1, screenWidth - channelWidth);
        hint.setText(compact ? "" : "  Left at current time opens categories  ·  OK previews / watches");
        renderRuler();
        adapter.notifyDataSetChanged();
    }

    private Button button(String name, Runnable action) {
        Button button = new Button(this);
        button.setText(name);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setMinimumHeight(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, background(SELECTED, GOLD));
        states.addState(new int[]{android.R.attr.state_pressed}, background(SELECTED, GOLD));
        states.addState(new int[]{}, background(INK, Color.TRANSPARENT));
        button.setBackground(states);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void renderRuler() {
        if (ruler == null) return;
        ruler.removeAllViews();
        boolean services = section.equals("Services");
        ruler.setVisibility(services ? View.GONE : View.VISIBLE);
        commands.setVisibility(services ? View.GONE : View.VISIBLE);
        if (services) return;
        TextView name = label(section.equals("Sports") ? "Sports channels" : category, 12, TEXT, true);
        name.setPadding(dp(12), 0, dp(4), 0);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        ruler.addView(name, new LinearLayout.LayoutParams(channelWidth, -1));
        for (int i = 0; i < slotCount(); i++) {
            TextView time = label(clock.format(new Date(start + i * SLOT)), 12, MUTED, false);
            time.setPadding(dp(7), 0, 0, 0);
            ruler.addView(time, new LinearLayout.LayoutParams(x(start + (i + 1) * SLOT) - x(start + i * SLOT), -1));
        }
    }

    private int slotCount() { return screenWidth < dp(640) ? 3 : screenWidth < dp(1050) ? 5 : 6; }
    private int x(long time) {
        return (int) Math.round(timelineWidth * ((double) (time - start) / (SLOT * slotCount())));
    }

    private Channel selected() { return row >= 0 && row < visible.size() ? visible.get(row) : null; }

    private void updateHero() {
        Channel channel = selected();
        Channel.Program program = channel == null ? null : channel.at(cursor);
        sourceLabel.setText(demo ? "SAMPLE GUIDE" : section.equals("Sports") ? "SPORTS" : "LIVE TV");
        String title = program != null ? program.title :
                channel != null ? channel.name : section.equals("Guide") ? "Live TV" : section;
        heading.setText(title);
        metadata.setText(channel == null ? "" : channel.name + (program == null ? "" :
                "  ·  " + clock.format(new Date(program.start)) + " – " + clock.format(new Date(program.end))));
        summary.setText(channel == null ? "" : program == null ? "Live channel" : program.description);
        String group = channel == null ? "" : channel.group + " " + channel.name;
        int artwork = section.equals("Sports") || isSports(channel) ? R.drawable.sports :
                group.toLowerCase(Locale.US).contains("news") ? R.drawable.news : R.drawable.entertainment;
        if (artwork != artId) { backdrop.setImageResource(artwork); artId = artwork; }
        sourceLabel.setText(loading ? "CONNECTING TO PETALIX CONTROL" : sourceLabel.getText());
    }

    private final class GridAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Channel getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View reusable, ViewGroup parent) {
            LinearLayout line = reusable instanceof LinearLayout ? (LinearLayout) reusable : new LinearLayout(GuideActivity.this);
            line.removeAllViews();
            line.setLayoutParams(new AbsListView.LayoutParams(-1, dp(44)));
            Channel channel = getItem(position);
            boolean focused = position == row && grid.hasFocus();
            TextView station = label(channel.name, 14, TEXT, true);
            station.setPadding(dp(12), 0, dp(7), 0);
            station.setSingleLine();
            station.setEllipsize(TextUtils.TruncateAt.END);
            station.setBackgroundColor(focused ? SELECTED : 0xff161b21);
            line.addView(station, new LinearLayout.LayoutParams(channelWidth, -1));
            long end = start + SLOT * slotCount();
            long edge = start;
            for (Channel.Program program : channel.programs) {
                if (program.end <= edge) continue;
                if (program.start >= end) break;
                long from = Math.max(edge, program.start);
                if (from > edge) addCell(line, "No listing", edge, from, focused);
                long to = Math.min(end, program.end);
                addCell(line, program.title, from, to, focused);
                edge = to;
            }
            if (edge < end) addCell(line, "No listing", edge, end, focused);
            return line;
        }
    }

    private void addCell(LinearLayout line, String title, long from, long to, boolean focused) {
        int size = x(to) - x(from);
        if (size < 1) return;
        boolean selected = focused && cursor >= from && cursor < to;
        TextView cell = label(title, 13, TEXT, selected);
        cell.setSingleLine();
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setPadding(dp(8), 0, dp(4), 0);
        cell.setBackground(background(selected ? SELECTED : CELL, selected ? GOLD : 0xff171b21));
        line.addView(cell, new LinearLayout.LayoutParams(size, -1));
    }

    private void populate() {
        visible = new ArrayList<>();
        for (Channel item : channels) {
            if (isVod(item)) continue;
            if (section.equals("Sports")) {
                if (isSports(item) && (category.equals("All channels") || category.equals(item.group))) visible.add(item);
            } else if (category.equals("My Locals")) {
                if (isLocal(item)) visible.add(item);
            } else if (category.equals("All channels") ? !isEventGroup(item.group) : category.equals(item.group)) {
                visible.add(item);
            }
        }
        row = 0;
        grid.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
        blank.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        blank.setText(section.equals("Sports") ? "No sports channels in the assigned lineup" :
                category.equals("My Locals") ? "No matching local stations in the assigned lineup" : "No channels in this category");
        updateHero();
        renderRuler();
        adapter.notifyDataSetChanged();
        grid.setSelection(0);
        renderSportsShelf();
    }

    private void renderSportsShelf() {
        sportsShelf.removeAllViews();
        if (!section.equals("Sports") || visible.isEmpty() || screenHeight < dp(500)) {
            sportsShelf.setVisibility(View.GONE);
            return;
        }
        sportsShelf.setVisibility(View.VISIBLE);
        LinearLayout events = new LinearLayout(this);
        events.setPadding(dp(10), 0, dp(10), dp(4));
        long now = System.currentTimeMillis();
        for (int index = 0; index < visible.size() && events.getChildCount() < 12; index++) {
            Channel channel = visible.get(index);
            Channel.Program airing = channel.at(now);
            if (airing == null) continue;
            final int position = index;
            Button event = button(airing.title + "\n" + channel.name, () -> {
                row = position;
                cursor = System.currentTimeMillis();
                grid.requestFocus();
                grid.setSelection(row);
                selectChannel();
            });
            event.setMaxLines(2);
            event.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams width = new LinearLayout.LayoutParams(dp(184), -1);
            width.setMargins(dp(3), dp(2), dp(3), dp(2));
            events.addView(event, width);
        }
        if (events.getChildCount() == 0) sportsShelf.setVisibility(View.GONE);
        sportsShelf.addView(events);
    }

    private static boolean isSports(Channel channel) {
        if (channel == null) return false;
        String group = channel.group.toLowerCase(Locale.US);
        String name = channel.name.toLowerCase(Locale.US);
        return group.matches(".*(sport|nfl|nba|mlb|nhl|ncaa|football|soccer|ufc|ppv|fanduel|tennis|basketball|hockey|baseball|espn|tsn|golf).*")
                || name.matches(".*(espn|fox sports|nbc sports|fanduel|nfl network|nba tv|nhl network|mlb network|tsn|bein sports).*");
    }

    private static boolean isVod(Channel channel) {
        String group = channel.group.toLowerCase(Locale.US);
        return group.matches(".*\\b(vod|on demand)\\b.*") ||
                (channel.stream != null && channel.stream.matches("(?i)https?://[^/]+/(?:movie|series)/.*"));
    }

    private static boolean isEventGroup(String group) {
        String value = group.toLowerCase(Locale.US);
        return value.matches(".*\\b(nfl|nba|mlb|nhl|ncaa|ncaaf|ncaab|soccer|ufc|ppv)\\b.*\\b(games|teams|events|replays|ppv)\\b.*")
                || value.matches(".*\\b(sports events|league pass|sunday ticket|ppv|espn\\+|btn\\+)\\b.*");
    }

    private boolean isLocal(Channel channel) {
        String value = (channel.name + " " + channel.epg).toUpperCase(Locale.US);
        for (String token : value.split("[^A-Z0-9]+")) if (localCalls.contains(token)) return true;
        return false;
    }

    private void categories() {
        SortedSet<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Channel channel : channels) {
            if (!isVod(channel) && !isEventGroup(channel.group) &&
                    (!section.equals("Sports") || isSports(channel))) groups.add(channel.group);
        }
        List<String> names = new ArrayList<>();
        names.add("All channels");
        if (!section.equals("Sports") && !localCalls.isEmpty()) names.add("My Locals");
        names.addAll(groups);
        new AlertDialog.Builder(this).setTitle("Categories").setItems(names.toArray(new String[0]), (d, index) -> {
            category = names.get(index);
            populate();
            grid.requestFocus();
        }).show();
    }

    private void now() {
        cursor = System.currentTimeMillis();
        start = cursor - Math.floorMod(cursor, SLOT);
        renderRuler();
        updateHero();
        adapter.notifyDataSetChanged();
        grid.requestFocus();
    }

    private void show(String tab) {
        if (body.getChildCount() > 2) body.removeViewAt(2);
        if (tab.equals("Services")) {
            section = tab;
            guide.setVisibility(View.GONE);
            ScrollView scroll = new ScrollView(this);
            LinearLayout shelf = column();
            shelf.setPadding(dp(24), dp(15), dp(24), dp(12));
            shelf.addView(label("Streaming services", 24, TEXT, true));
            LinearLayout row = null;
            String[] names = {"Netflix", "Prime Video", "Disney+", "Hulu", "BritBox"};
            String[] packages = {"com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
                    "com.hulu.livingroomplus", "com.britbox.us"};
            int[] art = {R.drawable.tile_netflix, R.drawable.tile_prime, R.drawable.tile_disney,
                    R.drawable.tile_hulu, R.drawable.tile_britbox};
            int columns = screenWidth < dp(640) ? 2 : 5;
            for (int i = 0; i < names.length; i++) {
                if (i % columns == 0) {
                    row = new LinearLayout(this);
                    shelf.addView(row, new LinearLayout.LayoutParams(-1, dp(112)));
                }
                final int service = i;
                ImageButton tile = new ImageButton(this);
                tile.setImageResource(art[i]);
                tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tile.setContentDescription(names[i]);
                android.graphics.drawable.StateListDrawable focus = new android.graphics.drawable.StateListDrawable();
                focus.addState(new int[]{android.R.attr.state_focused}, background(SELECTED, GOLD));
                focus.addState(new int[]{}, background(CELL, INK));
                tile.setBackground(focus);
                tile.setPadding(dp(4), dp(4), dp(4), dp(4));
                tile.setOnClickListener(v -> launchService(names[service], packages[service]));
                row.addView(tile, new LinearLayout.LayoutParams(0, -1, 1));
            }
            scroll.addView(shelf);
            body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            hero.setVisibility(View.GONE);
        } else {
            section = tab;
            category = "All channels";
            hero.setVisibility(View.VISIBLE);
            layout();
            populate();
            grid.requestFocus();
        }
    }

    private void launchService(String name, String pkg) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) startActivity(intent);
        else Toast.makeText(this, name + " is not installed on this device.", Toast.LENGTH_SHORT).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (full) {
            if (key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_CHANNEL_DOWN ||
                    key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && !visible.isEmpty()) {
                    int delta = key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1;
                    row = Math.floorMod(row + delta, visible.size());
                    tune(selected());
                }
                return true;
            }
        } else if (grid != null && grid.hasFocus() && selected() != null && !section.equals("Services")) {
            boolean select = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
            if (select || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
                if (select) {
                    if (event.getRepeatCount() == 0) selectChannel();
                } else if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (key == KeyEvent.KEYCODE_DPAD_LEFT && cursor <= System.currentTimeMillis()) {
                        if (event.getRepeatCount() == 0) categories();
                    } else moveTime(key == KeyEvent.KEYCODE_DPAD_RIGHT);
                } else if (key == KeyEvent.KEYCODE_DPAD_UP && row == 0) {
                    commands.getChildAt(0).requestFocus();
                } else {
                    row = Math.min(visible.size() - 1, Math.max(0, row + (key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                    if (row < grid.getFirstVisiblePosition() || row > grid.getLastVisiblePosition()) grid.setSelection(row);
                    updateHero();
                    adapter.notifyDataSetChanged();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void moveTime(boolean forward) {
        Channel.Program program = selected().at(cursor);
        long min = System.currentTimeMillis();
        long next = program == null ? cursor + (forward ? SLOT : -SLOT) : forward ? program.end : program.start - 1;
        cursor = Math.max(min, Math.min(min + DAY, next));
        if (cursor < start || cursor >= start + slotCount() * SLOT) start = cursor - Math.floorMod(cursor, SLOT);
        renderRuler();
        updateHero();
        adapter.notifyDataSetChanged();
    }

    private void selectChannel() {
        Channel channel = selected();
        if (channel == null) return;
        if (demo) { account(); return; }
        Channel.Program program = channel.at(cursor);
        if (program != null && program.start > System.currentTimeMillis()) {
            new AlertDialog.Builder(this).setTitle(program.title)
                    .setMessage(clock.format(new Date(program.start)) + "\n\n" + program.description)
                    .setPositiveButton("Close", null).show();
            return;
        }
        if (tuned == channel && player != null && player.getPlayerError() == null) {
            full = true;
            hero.setVisibility(View.VISIBLE);
            layout();
        } else tune(channel);
    }

    private void tune(Channel channel) {
        if (channel == null || channel.stream == null) return;
        tuned = channel;
        if (player != null) play(channel);
        updateHero();
        layout();
    }

    private void play(Channel channel) {
        player.setMediaItem(MediaItem.fromUri(channel.stream));
        player.prepare();
        player.play();
    }

    @Override public void onBackPressed() {
        if (full) {
            full = false;
            now();
            layout();
            grid.setSelection(row);
        } else if (!section.equals("Guide")) show("Guide");
        else super.onBackPressed();
    }

    private void account() {
        if (loading) {
            Toast.makeText(this, "Connecting to Petalix Control", Toast.LENGTH_SHORT).show();
            return;
        }
        if (panel.activated()) {
            String[] actions = {"Refresh channels and listings", "Set ZIP code"};
            new AlertDialog.Builder(this).setTitle("Petalix Control").setItems(actions, (d, selected) -> {
                if (selected == 0) connect(null);
                else zip();
            }).show();
            return;
        }
        EditText pin = input("8-digit PIN", 8);
        new AlertDialog.Builder(this).setTitle("Sign in to Petalix")
                .setMessage("Enter a Petalix Control PIN assigned to this preview device.")
                .setView(pin).setPositiveButton("Connect", (dialog, button) -> {
                    String value = pin.getText().toString();
                    if (value.matches("[0-9]{8}")) connect(value);
                    else alert("Enter an 8-digit PIN.");
                }).setNegativeButton("Cancel", null).show();
        pin.requestFocus();
    }

    private EditText input(String hint, int length) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
        field.setSingleLine();
        return field;
    }

    private void zip() {
        EditText zip = input("5-digit ZIP", 5);
        zip.setText(getPreferences(0).getString("zip", ""));
        new AlertDialog.Builder(this).setTitle("Find local stations").setView(zip)
                .setPositiveButton("Find", (dialog, button) -> {
                    String value = zip.getText().toString();
                    if (!value.matches("[0-9]{5}")) { alert("Enter a 5-digit ZIP code."); return; }
                    worker.execute(() -> {
                        try {
                            Set<String> calls = panel.localCalls(value);
                            onScreen(() -> {
                                localCalls = calls;
                                getPreferences(0).edit().putString("zip", value).apply();
                                show("Guide");
                                category = "My Locals";
                                populate();
                                grid.requestFocus();
                            });
                        } catch (Exception e) {
                            onScreen(() -> alert("Could not match that ZIP code. Check it and try again."));
                        }
                    });
                }).setNegativeButton("Cancel", null).show();
    }

    private void connect(String pin) {
        if (loading) return;
        loading = true;
        updateHero();
        String savedZip = getPreferences(0).getString("zip", "");
        worker.execute(() -> {
            try {
                JSONObject account = pin == null ? panel.status() : panel.activate(pin);
                List<Channel> loaded = panel.channels(account);
                loaded.sort(Comparator.comparing(channel -> channel.name, String.CASE_INSENSITIVE_ORDER));
                onScreen(() -> {
                    loading = false;
                    demo = false;
                    channels = loaded;
                    if (player != null) player.stop();
                    tuned = null;
                    show("Guide");
                    if (pin != null && savedZip.isEmpty()) zip();
                });
                try {
                    Map<String, List<Channel.Program>> listings = panel.listings(account, loaded);
                    onScreen(() -> {
                        for (Channel channel : loaded) channel.programs.addAll(listings.getOrDefault(channel.epg, Collections.emptyList()));
                        updateHero();
                        adapter.notifyDataSetChanged();
                        renderSportsShelf();
                    });
                } catch (Exception ignored) {
                    onScreen(() -> Toast.makeText(this, "Channels loaded. The provider's listings are unavailable.", Toast.LENGTH_LONG).show());
                }
                if (!savedZip.isEmpty()) {
                    try {
                        Set<String> calls = panel.localCalls(savedZip);
                        onScreen(() -> localCalls = calls);
                    } catch (Exception ignored) { }
                }
            } catch (Exception error) {
                onScreen(() -> {
                    loading = false;
                    updateHero();
                    // General connection errors can contain credential-bearing provider URLs.
                    String message = error instanceof IOException ? error.getMessage() : null;
                    if (message == null || !(message.startsWith("Petalix Control") || message.startsWith("This PIN") ||
                            message.startsWith("This device") || message.startsWith("This Petalix") ||
                            message.startsWith("The assigned provider"))) {
                        message = "Unable to load channels from the assigned provider.";
                    }
                    alert(message);
                });
            }
        });
    }

    private void onScreen(Runnable action) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) action.run(); });
    }

    private void alert(String message) {
        new AlertDialog.Builder(this).setTitle("Petalix").setMessage(message).setPositiveButton("OK", null).show();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable background(int color, int outline) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(4));
        shape.setStroke(dp(1), outline);
        return shape;
    }

    private int dp(float size) { return Math.round(size * getResources().getDisplayMetrics().density); }

    private static List<Channel> exampleChannels(long start) {
        String[][] lineup = {
                {"City News", "News", "The Evening Report", "The day's stories from around the region.", "Weather Desk", "City in Focus"},
                {"Stadium One", "Sports", "College Basketball", "Live coverage from the court.", "Postgame", "College Football"},
                {"Arena Sports", "Sports", "Matchday Live", "The latest action from the pitch.", "Highlights", "Sports Tonight"},
                {"Studio 7", "Entertainment", "Inside the Studio", "Conversations with today's performers.", "On Set", "After Hours"},
                {"Field Notes", "Documentary", "Along the Ridge", "A journey into the mountains.", "Wild Rivers", "Hidden Coast"},
                {"Local View", "News", "Around Town", "People and stories close to home.", "Community Calendar", "Night Desk"},
                {"Classic Cinema", "Movies", "The Sunday Train", "A small-town mystery unfolds on the last train out.", "Film Notes", "Evening Cinema"},
                {"World Report", "News", "Across the Globe", "Reports from around the world.", "World Business", "The Headlines"}
        };
        List<Channel> data = new ArrayList<>();
        for (String[] item : lineup) {
            Channel channel = new Channel(item[0], item[0], item[1], "", null);
            channel.programs.add(new Channel.Program(start, start + SLOT * 2, item[2], item[3]));
            channel.programs.add(new Channel.Program(start + SLOT * 2, start + SLOT * 3, item[4], ""));
            channel.programs.add(new Channel.Program(start + SLOT * 3, start + SLOT * 6, item[5], ""));
            data.add(channel);
        }
        return data;
    }
}
