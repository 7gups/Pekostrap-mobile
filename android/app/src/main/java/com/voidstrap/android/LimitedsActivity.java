package com.voidstrap.android;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The signed-in account's limiteds, with search, a category filter, and "Load more" paging. */
public final class LimitedsActivity extends AppCompatActivity {
    private static final int PAGE_SIZE = 24;
    private static final String[] CATEGORY_NAMES = {"All", "Hats", "Hair", "Face", "Neck", "Shoulder", "Front", "Back", "Waist", "Heads", "Faces", "Gears", "Decals"};
    private static final String[] CATEGORY_IDS = {"null", "8", "41", "42", "43", "44", "45", "46", "47", "17", "18", "19", "13"};

    private final List<JSONObject> items = new ArrayList<>();
    private final Map<Long, String> thumbs = new HashMap<>();
    private Store store;
    private long userId;
    private String cursor = "";
    private String nextCursor;
    private boolean loading;
    private String category = "null";
    private LinearLayout list;
    private TextView status;
    private MaterialButton more;
    private EditText search;
    private int sortMode;
    private boolean gridMode;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_limiteds);
        store = Store.get(this);
        userId = PekoraSession.userId(this);
        if (userId <= 0) {
            finish();
            return;
        }
        list = findViewById(R.id.limiteds_list);
        status = findViewById(R.id.limiteds_status);
        more = findViewById(R.id.limiteds_more);
        search = findViewById(R.id.limiteds_search);
        Spinner spinner = findViewById(R.id.limiteds_category);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORY_NAMES));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String next = CATEGORY_IDS[position];
                if (!next.equals(category)) {
                    category = next;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        Spinner sort = findViewById(R.id.limiteds_sort);
        sort.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.limiteds_sort_options)));
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != sortMode) {
                    sortMode = position;
                    render();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        Spinner view = findViewById(R.id.limiteds_view);
        view.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{getString(R.string.limiteds_view_list), getString(R.string.limiteds_view_grid)}));
        view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                boolean grid = position == 1;
                if (grid != gridMode) {
                    gridMode = grid;
                    render();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        more.setOnClickListener(x -> loadPage());
        reload();
    }

    private void reload() {
        items.clear();
        thumbs.clear();
        cursor = "";
        nextCursor = null;
        render();
        loadPage();
    }

    private void loadPage() {
        if (loading) return;
        loading = true;
        status.setText(R.string.limiteds_loading);
        more.setVisibility(View.GONE);
        String requestCursor = cursor;
        String requestCategory = category;
        Context app = getApplicationContext();
        store.work.execute(() -> {
            JSONObject page = PekoraApi.collectibles(userId, requestCursor, PAGE_SIZE, requestCategory, null);
            JSONArray data = page == null ? null : page.optJSONArray("data");
            List<JSONObject> got = new ArrayList<>();
            Map<Long, String> urls = new HashMap<>();
            if (data != null) {
                StringBuilder ids = new StringBuilder();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject o = data.optJSONObject(i);
                    if (o == null) continue;
                    got.add(o);
                    if (ids.length() > 0) ids.append(',');
                    ids.append(o.optLong("assetId", 0));
                }
                if (ids.length() > 0) {
                    JSONObject t = PekoraApi.assetThumbs(ids.toString(), "150x150");
                    JSONArray td = t == null ? null : t.optJSONArray("data");
                    for (int i = 0; td != null && i < td.length(); i++) {
                        JSONObject x = td.optJSONObject(i);
                        if (x != null && "Completed".equals(x.optString("state", "")) && x.optString("imageUrl", "").startsWith("https://")) {
                            urls.put(x.optLong("targetId", 0), x.optString("imageUrl", ""));
                        }
                    }
                }
            }
            String next = page == null ? "" : page.optString("nextPageCursor", "");
            boolean failed = data == null;
            runOnUiThread(() -> {
                loading = false;
                if (failed) {
                    status.setText(R.string.limiteds_failed);
                    more.setVisibility(nextCursor != null ? View.VISIBLE : View.GONE);
                    return;
                }
                items.addAll(got);
                thumbs.putAll(urls);
                nextCursor = next.isEmpty() || next.equals("null") ? null : next;
                if (nextCursor != null) cursor = nextCursor;
                render();
            });
        });
    }

    private void render() {
        list.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<JSONObject> visible = new ArrayList<>();
        for (JSONObject o : items) {
            if (q.isEmpty() || o.optString("name", "").toLowerCase(Locale.ROOT).contains(q)) visible.add(o);
        }
        sortItems(visible);
        if (gridMode) renderGrid(visible);
        else for (JSONObject o : visible) list.addView(row(o));
        int shown = visible.size();
        if (items.isEmpty() && !loading) status.setText(R.string.limiteds_empty);
        else if (shown == 0 && !q.isEmpty()) status.setText(R.string.limiteds_no_match);
        else status.setText(getString(R.string.limiteds_count, shown));
        more.setVisibility(nextCursor != null && !loading ? View.VISIBLE : View.GONE);
    }

    private interface Key {
        Long of(JSONObject o);
    }

    private static final Key RAP = o -> o.isNull("recentAveragePrice") ? null : o.optLong("recentAveragePrice", 0);
    private static final Key SERIAL = o -> o.isNull("serialNumber") ? null : o.optLong("serialNumber", 0);

    /** Sorts by RAP or serial. Items without that value always go last. */
    private void sortItems(List<JSONObject> out) {
        Key key;
        boolean descending;
        switch (sortMode) {
            case 1: key = RAP; descending = true; break;
            case 2: key = RAP; descending = false; break;
            case 3: key = SERIAL; descending = false; break;
            case 4: key = SERIAL; descending = true; break;
            default: return;
        }
        Collections.sort(out, (a, b) -> {
            Long x = key.of(a), y = key.of(b);
            if (x == null && y == null) return 0;
            if (x == null) return 1;
            if (y == null) return -1;
            return descending ? Long.compare(y, x) : Long.compare(x, y);
        });
    }

    private void renderGrid(List<JSONObject> visible) {
        float d = getResources().getDisplayMetrics().density;
        int margin = Math.round(4 * d);
        LinearLayout pair = null;
        for (int i = 0; i < visible.size(); i++) {
            if (i % 2 == 0) {
                pair = new LinearLayout(this);
                pair.setOrientation(LinearLayout.HORIZONTAL);
                list.addView(pair, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(margin, margin, margin, margin);
            pair.addView(cell(visible.get(i)), lp);
        }
        if (visible.size() % 2 == 1 && pair != null) {
            pair.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        }
    }

    private View cell(JSONObject o) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(8 * d);
        cell.setPadding(pad, pad, pad, pad);

        ImageView img = new ImageView(this);
        String url = thumbs.get(o.optLong("assetId", 0));
        if (url != null) Net.image(img, url, R.color.vs_subtle_pressed, Math.round(120 * d));
        cell.addView(img, new LinearLayout.LayoutParams(Math.round(120 * d), Math.round(120 * d)));

        TextView name = new TextView(this);
        name.setText(o.optString("name", ""));
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(2);
        cell.addView(name);

        TextView rap = new TextView(this);
        String price = o.isNull("recentAveragePrice") ? "--" : String.format(Locale.getDefault(), "%,d", o.optLong("recentAveragePrice", 0));
        rap.setText(getString(R.string.limiteds_rap, price));
        cell.addView(rap);

        if (!o.isNull("serialNumber")) {
            TextView serial = new TextView(this);
            String copies = o.isNull("copyCount") ? "--" : String.valueOf(o.optLong("copyCount", 0));
            serial.setText(getString(R.string.limiteds_serial, String.valueOf(o.optLong("serialNumber", 0)), copies));
            cell.addView(serial);
        }
        return cell;
    }

    private View row(JSONObject o) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(10 * d);
        row.setPadding(pad, pad, pad, pad);

        ImageView img = new ImageView(this);
        String url = thumbs.get(o.optLong("assetId", 0));
        if (url != null) Net.image(img, url, R.color.vs_subtle_pressed, Math.round(56 * d));
        row.addView(img, new LinearLayout.LayoutParams(Math.round(56 * d), Math.round(56 * d)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Math.round(12 * d), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(o.optString("name", ""));
        name.setTypeface(null, Typeface.BOLD);
        text.addView(name);

        TextView rap = new TextView(this);
        String price = o.isNull("recentAveragePrice") ? "--" : String.format(Locale.getDefault(), "%,d", o.optLong("recentAveragePrice", 0));
        rap.setText(getString(R.string.limiteds_rap, price));
        text.addView(rap);

        if (!o.isNull("serialNumber")) {
            TextView serial = new TextView(this);
            String copies = o.isNull("copyCount") ? "--" : String.valueOf(o.optLong("copyCount", 0));
            serial.setText(getString(R.string.limiteds_serial, String.valueOf(o.optLong("serialNumber", 0)), copies));
            text.addView(serial);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }
}
