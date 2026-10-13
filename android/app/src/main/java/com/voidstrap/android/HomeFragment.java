package com.voidstrap.android;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;


import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

public final class HomeFragment extends Page {
    private static final long FEED_REFRESH_MS = 10 * 60_000L;
    private static final long FEED_RETRY_MS = 2 * 60_000L;
    private static final long GITHUB_REFRESH_MS = 30 * 60_000L;
    private static final long WELCOME_REFRESH_MS = 60 * 60_000L;
    private static final long ACCOUNT_REFRESH_MS = 10 * 60_000L;
    private static final long ALERT_REFRESH_MS = 30 * 60_000L;
    private static final String GITHUB_API = "https://api.github.com/repos/7gups/Pekostrap-mobile";
    private static final String GITHUB_URL = "https://github.com/7gups/Pekostrap-mobile";
    private static final String[] GITHUB_HEADERS = {"User-Agent", "Voidstrap Android", "Accept", "application/vnd.github+json"};

    private View newsHeader;
    private TextView newsCount;
    private LinearLayout newsGrid;
    private TextView catalogStatus;
    private MaterialButton catalogSignIn;
    private LinearLayout catalogGrid;

    private long newsDueAt;
    private long catalogDueAt;
    private TextView githubStarsValue;
    private TextView githubCommitsValue;
    private long githubDueAt;
    private TextView welcome;
    private long welcomeDueAt;
    private View accountCard;
    private TextView accountRobux;
    private TextView accountTickets;
    private TextView accountFriendRequests;
    private TextView accountTrades;
    private TextView accountMessages;
    private long accountDueAt;
    private View alertBanner;
    private TextView alertText;
    private MaterialButton alertLink;
    private String alertUrl;
    private long alertDueAt;
    private String githubSite;
    private Release githubRelease;
    private TextView githubReleaseTitle;
    private TextView githubReleaseMeta;
    private TextView githubReleaseNotes;
    private MaterialButton githubReleaseOpen;

    private final android.net.ConnectivityManager.NetworkCallback online = new android.net.ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull android.net.Network network) {
            store.main.post(HomeFragment.this::reconnected);
        }

        @Override
        public void onBlockedStatusChanged(@NonNull android.net.Network network, boolean blocked) {
            if (!blocked) store.main.post(HomeFragment.this::reconnected);
        }
    };
    private android.net.ConnectivityManager watching;
    private long watchingSince;

    private static final class Release {
        String tag = "";
        String name = "";
        String date = "";
        String notes = "";
        String url = "";
    }

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onStart() {
        super.onStart();
        android.net.ConnectivityManager cm = requireContext().getSystemService(android.net.ConnectivityManager.class);
        if (cm == null) return;
        watchingSince = android.os.SystemClock.elapsedRealtime();
        try {
            cm.registerDefaultNetworkCallback(online);
            watching = cm;
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onStop() {
        if (watching != null) {
            try {
                watching.unregisterNetworkCallback(online);
            } catch (RuntimeException ignored) {
            }
            watching = null;
        }
        super.onStop();
    }

    private void reconnected() {
        if (!isAdded() || getView() == null || isHidden()) return;
        if (android.os.SystemClock.elapsedRealtime() - watchingSince < 3000) return;
        if (newsGrid.getChildCount() > 0 && catalogGrid.getChildCount() > 0) return;
        newsDueAt = 0;
        catalogDueAt = 0;
        refresh();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        newsHeader = v.findViewById(R.id.news_header);
        newsCount = v.findViewById(R.id.news_count);
        newsGrid = v.findViewById(R.id.news_grid);
        catalogStatus = v.findViewById(R.id.catalog_status);
        catalogGrid = v.findViewById(R.id.catalog_grid);
        catalogSignIn = v.findViewById(R.id.catalog_sign_in);
        catalogSignIn.setOnClickListener(x -> PekoraSession.toggle(requireActivity()));
        welcome = v.findViewById(R.id.home_welcome);
        accountCard = v.findViewById(R.id.account_card);
        accountRobux = v.findViewById(R.id.account_robux);
        accountTickets = v.findViewById(R.id.account_tickets);
        accountFriendRequests = v.findViewById(R.id.account_friend_requests);
        accountTrades = v.findViewById(R.id.account_trades);
        accountMessages = v.findViewById(R.id.account_messages);
        alertBanner = v.findViewById(R.id.alert_banner);
        alertText = v.findViewById(R.id.alert_text);
        alertLink = v.findViewById(R.id.alert_link);
        alertLink.setOnClickListener(x -> openAlertLink());
        githubStarsValue = v.findViewById(R.id.github_stars_value);
        githubCommitsValue = v.findViewById(R.id.github_commits_value);
        v.findViewById(R.id.github_open).setOnClickListener(x -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))));
        v.findViewById(R.id.github_website).setOnClickListener(x -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(githubSite != null && !githubSite.isEmpty() ? githubSite : GITHUB_URL))));
        v.findViewById(R.id.github_refresh).setOnClickListener(x -> loadGithub());
        githubReleaseTitle = v.findViewById(R.id.release_title);
        githubReleaseMeta = v.findViewById(R.id.release_meta);
        githubReleaseNotes = v.findViewById(R.id.release_notes);
        githubReleaseOpen = v.findViewById(R.id.release_open);
        v.findViewById(R.id.news_explore).setOnClickListener(x -> exploreNews());
        newsDueAt = 0;
        catalogDueAt = 0;
    }

    @Override
    protected void refresh() {
        if (catalogSignIn != null) catalogSignIn.setText(PekoraSession.signedIn(requireContext()) ? R.string.pekora_sign_out : R.string.pekora_sign_in);
        long now = System.currentTimeMillis();
        if (now >= newsDueAt) loadNews();
        if (now >= catalogDueAt) loadCatalog();
        if (now >= githubDueAt) loadGithub();
        bindWelcome();
        if (now >= welcomeDueAt && PekoraSession.signedIn(requireContext())) loadWelcome();
        boolean in = PekoraSession.signedIn(requireContext());
        if (accountCard != null) accountCard.setVisibility(in ? View.VISIBLE : View.GONE);
        if (in && now >= accountDueAt) loadAccount();
        if (now >= alertDueAt) loadAlert();
    }

    private int contentDp() {
        Configuration cfg = getResources().getConfiguration();
        int w = cfg.screenWidthDp;
        int nav = w >= 840 ? 240 : w >= 600 ? 80 : 0;
        int inset = w >= 600 ? 24 : 16;
        return Math.max(200, w - nav - inset * 2);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void fill(LinearLayout container, List<View> cells, int cols, int gapDp) {
        container.removeAllViews();
        int gap = Ui.dp(requireContext(), gapDp);
        for (int i = 0; i < cells.size(); i += cols) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            for (int k = 0; k < cols; k++) {
                View cell = i + k < cells.size() ? cells.get(i + k) : new Space(requireContext());
                ViewGroup.LayoutParams own = cell.getLayoutParams();
                int height = own != null && own.height > 0 ? own.height : ViewGroup.LayoutParams.WRAP_CONTENT;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, 1f);
                if (k < cols - 1) lp.setMarginEnd(gap);
                row.addView(cell, lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i + cols < cells.size()) rp.bottomMargin = gap;
            container.addView(row, rp);
        }
    }

    private void loadNews() {
        newsDueAt = System.currentTimeMillis() + FEED_REFRESH_MS;
        Context app = requireContext().getApplicationContext();
        store.work.execute(() -> {
            List<HomeFeed.News> news = null;
            try {
                news = HomeFeed.latestNews(app, 2);
            } catch (IOException | RuntimeException e) {
                android.util.Log.w("Voidstrap", "News feed failed", e);
            }
            List<HomeFeed.News> n = news;
            store.main.post(() -> bindNews(n));
        });
    }

    private void loadCatalog() {
        catalogDueAt = System.currentTimeMillis() + FEED_REFRESH_MS;
        Context app = requireContext().getApplicationContext();
        if (catalogGrid.getChildCount() == 0) {
            catalogStatus.setVisibility(View.VISIBLE);
            catalogStatus.setText(R.string.home_catalog_loading);
        }
        store.work.execute(() -> {
            List<HomeFeed.Item> items = null;
            try {
                items = HomeFeed.catalog(app);
            } catch (IOException | RuntimeException e) {
                android.util.Log.w("Voidstrap", "Catalog feed failed", e);
            }
            List<HomeFeed.Item> it = items;
            store.main.post(() -> bindCatalog(it));
        });
    }

    private void bindNews(List<HomeFeed.News> news) {
        if (getView() == null) return;
        if (news == null) newsDueAt = System.currentTimeMillis() + FEED_RETRY_MS;
        if (news == null && newsGrid.getChildCount() > 0) return;
        boolean has = news != null && !news.isEmpty();
        newsHeader.setVisibility(has ? View.VISIBLE : View.GONE);
        newsGrid.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) return;
        int recent = 0;
        for (HomeFeed.News n : news) if (n.created > 0 && System.currentTimeMillis() - n.created < 7 * DateUtils.DAY_IN_MILLIS) recent++;
        android.text.SpannableStringBuilder title = new android.text.SpannableStringBuilder(getString(R.string.home_news_title));
        if (recent > 0) {
            int start = title.length();
            title.append("  ").append(getResources().getQuantityString(R.plurals.home_news_count, recent, recent));
            title.setSpan(new android.text.style.AbsoluteSizeSpan(13, true), start, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setSpan(new android.text.style.ForegroundColorSpan(Ui.attr(requireContext(), R.attr.vsTextTertiary)), start, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), start, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        newsCount.setText(title);
        List<View> cells = new ArrayList<>();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (HomeFeed.News n : news) {
            View card = inf.inflate(R.layout.item_news_card, newsGrid, false);
            ((TextView) card.findViewById(R.id.news_title)).setText(HomeFeed.cardTitle(n.title));
            String date = newsDate(n.created);
            ((TextView) card.findViewById(R.id.news_date)).setText(date);
            View pill = card.findViewById(R.id.news_tag_pill);
            pill.setVisibility(n.tag.isEmpty() ? View.GONE : View.VISIBLE);
            ((TextView) card.findViewById(R.id.news_tag)).setText(n.tag);
            ImageView image = card.findViewById(R.id.news_image);
            Net.image(image, HomeFeed.thumbnail(n.image, 760), R.color.vs_news_surface, Ui.dp(requireContext(), 380));
            card.setContentDescription(getString(R.string.home_news_description, n.title, date));
            card.setOnClickListener(x -> Ui.openWeb(requireContext(), n.url));
            cells.add(card);
        }
        fill(newsGrid, cells, contentDp() >= 560 ? 2 : 1, 14);
    }

    private String newsDate(long created) {
        if (created <= 0) return getString(R.string.home_news_announcement);
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(created);
        Calendar now = Calendar.getInstance();
        long days = (startOfDay(now) - startOfDay(then)) / DateUtils.DAY_IN_MILLIS;
        if (days <= 0) return getString(R.string.home_news_today);
        if (days == 1) return getString(R.string.home_news_yesterday);
        if (days < 7) return getResources().getQuantityString(R.plurals.home_news_days_ago, (int) days, (int) days);
        return new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date(created)).toLowerCase(Locale.getDefault());
    }

    private static long startOfDay(Calendar c) {
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    private void bindCatalog(List<HomeFeed.Item> items) {
        if (getView() == null) return;
        if (items == null) catalogDueAt = System.currentTimeMillis() + FEED_RETRY_MS;
        if (items == null || items.isEmpty()) {
            if (catalogGrid.getChildCount() > 0) return;
            catalogStatus.setVisibility(View.VISIBLE);
            catalogStatus.setText(items == null ? R.string.home_catalog_failed : R.string.home_catalog_empty);
            return;
        }
        catalogStatus.setVisibility(View.GONE);
        List<View> cells = new ArrayList<>();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (HomeFeed.Item it : items) {
            View card = inf.inflate(R.layout.item_catalog_card, catalogGrid, false);
            ((TextView) card.findViewById(R.id.catalog_name)).setText(it.name);
            ((TextView) card.findViewById(R.id.catalog_creator)).setText(it.creator);
            String price = HomeFeed.price(it);
            ((TextView) card.findViewById(R.id.catalog_price)).setText(price == null ? getString(R.string.home_free) : price);
            card.findViewById(R.id.catalog_limited).setVisibility(it.limited ? View.VISIBLE : View.GONE);
            TextView type = card.findViewById(R.id.catalog_type);
            type.setText(it.type);
            type.setVisibility(it.type.isEmpty() ? View.GONE : View.VISIBLE);
            Net.image(card.findViewById(R.id.catalog_image), it.image, R.color.vs_subtle, Ui.dp(requireContext(), 150));
            card.setContentDescription(getString(R.string.home_catalog_description, it.name, it.creator, price == null ? getString(R.string.home_free) : price));
            card.setOnClickListener(x -> Actions.openInRoblox(host(), it.appLink(), it.link()));
            cells.add(card);
        }
        fill(catalogGrid, cells, clamp(contentDp() / 150, 2, 5), 8);
    }

    private void bindWelcome() {
        if (welcome == null) return;
        String name = PekoraSession.signedIn(requireContext()) ? store.setting(PekoraSession.NAME_SETTING, "") : "";
        welcome.setText(getString(R.string.home_welcome, name));
        welcome.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadWelcome() {
        welcomeDueAt = System.currentTimeMillis() + WELCOME_REFRESH_MS;
        Context app = requireContext().getApplicationContext();
        store.work.execute(() -> {
            String name = PekoraSession.fetchUsername(app);
            store.main.post(() -> {
                if (name != null) store.putSetting(PekoraSession.NAME_SETTING, name);
                bindWelcome();
            });
        });
    }

    private String count(long v) {
        return v < 0 ? getString(R.string.home_github_empty) : String.format(Locale.getDefault(), "%,d", v);
    }

    private void loadAccount() {
        accountDueAt = System.currentTimeMillis() + ACCOUNT_REFRESH_MS;
        Context app = requireContext().getApplicationContext();
        store.work.execute(() -> {
            String session = PekoraSession.session(app);
            if (session == null) {
                store.main.post(this::clearAccount);
                return;
            }
            JSONObject me = PekoraApi.me(session);
            long id = me == null ? 0 : me.optLong("id", 0);
            if (id <= 0) {
                // Session rejected or the call failed: show dashes instead of stale numbers.
                accountDueAt = System.currentTimeMillis() + FEED_RETRY_MS;
                store.main.post(this::clearAccount);
                return;
            }
            JSONObject cur = PekoraApi.currency(id, session);
            long robux = cur == null ? -1 : cur.optLong("robux", -1);
            long tickets = cur == null ? -1 : cur.optLong("tickets", -1);
            long friends = PekoraApi.friendRequests(session);
            long trades = PekoraApi.tradesInbound(session);
            long messages = PekoraApi.unreadMessages(session);
            store.main.post(() -> {
                accountRobux.setText(count(robux));
                accountTickets.setText(count(tickets));
                accountFriendRequests.setText(count(friends));
                accountTrades.setText(count(trades));
                accountMessages.setText(count(messages));
            });
        });
    }

    private void clearAccount() {
        if (accountRobux == null) return;
        String dash = getString(R.string.home_github_empty);
        accountRobux.setText(dash);
        accountTickets.setText(dash);
        accountFriendRequests.setText(dash);
        accountTrades.setText(dash);
        accountMessages.setText(dash);
    }

    private void loadAlert() {
        alertDueAt = System.currentTimeMillis() + ALERT_REFRESH_MS;
        store.work.execute(() -> {
            JSONObject info = PekoraApi.alertInfo();
            store.main.post(() -> bindAlert(info));
        });
    }

    private void bindAlert(JSONObject info) {
        if (alertBanner == null) return;
        String text = info == null ? "" : info.optString("Text", "");
        boolean show = info != null && info.optBoolean("IsVisible", false) && !text.isEmpty();
        alertBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        alertText.setText(text);
        String link = info.optString("LinkText", "");
        String url = info.optString("LinkUrl", "");
        alertUrl = PekoraApi.safeLink(url) ? url : null;
        boolean showLink = alertUrl != null && !link.isEmpty();
        alertLink.setText(link);
        alertLink.setVisibility(showLink ? View.VISIBLE : View.GONE);
    }

    private void openAlertLink() {
        if (alertUrl != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(alertUrl)));
    }

    private void loadGithub() {
        githubDueAt = System.currentTimeMillis() + GITHUB_REFRESH_MS;
        store.work.execute(() -> {
            Integer stars = null;
            String site = null;
            Integer commits = null;
            Release rel = null;
            boolean relFailed = false;
            try {
                JSONObject repo = new JSONObject(Net.text(GITHUB_API, 200_000));
                stars = repo.getInt("stargazers_count");
                site = repo.optString("homepage", "");
            } catch (Exception e) {
                android.util.Log.w("Voidstrap", "GitHub repo failed", e);
            }
            try {
                commits = githubCommits();
            } catch (Exception e) {
                android.util.Log.w("Voidstrap", "GitHub commits failed", e);
            }
            try {
                rel = githubRelease();
            } catch (Exception e) {
                relFailed = true;
                android.util.Log.w("Voidstrap", "GitHub release failed", e);
            }
            Integer s = stars, c = commits;
            String h = site;
            Release r = rel;
            boolean f = relFailed;
            store.main.post(() -> bindGithub(s, c, h, r, f));
        });
    }

    /** Total commits on the default branch: with per_page=1, the last page number equals the commit count. */
    private static int githubCommits() throws Exception {
        Object[] res = Http.request("GET", GITHUB_API + "/commits?per_page=1", GITHUB_HEADERS, null, 15_000, 200_000, true);
        if (res == null) throw new java.io.IOException("GitHub request failed");
        int code = ((int[]) res[0])[0];
        if (code != 200) throw new java.io.IOException("HTTP " + code);
        String[] pairs = (String[]) res[1];
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (!"link".equalsIgnoreCase(pairs[i])) continue;
            Matcher m = Pattern.compile("[?&]page=(\\d+)>; rel=\"last\"").matcher(pairs[i + 1]);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return new JSONArray(new String((byte[]) res[2], StandardCharsets.UTF_8)).length();
    }

    /** Latest published release, or null when the repo has none yet (GitHub answers 404). */
    private static Release githubRelease() throws Exception {
        Object[] res = Http.request("GET", GITHUB_API + "/releases/latest", GITHUB_HEADERS, null, 15_000, 400_000, true);
        if (res == null) throw new java.io.IOException("GitHub request failed");
        int code = ((int[]) res[0])[0];
        if (code == 404) return null;
        if (code != 200) throw new java.io.IOException("HTTP " + code);
        JSONObject o = new JSONObject(new String((byte[]) res[2], StandardCharsets.UTF_8));
        Release r = new Release();
        r.tag = o.optString("tag_name", "");
        r.name = o.optString("name", "");
        String published = o.optString("published_at", "");
        r.date = published.length() >= 10 ? published.substring(0, 10) : published;
        r.notes = plainNotes(o.optString("body", ""));
        r.url = o.optString("html_url", GITHUB_URL + "/releases");
        return r;
    }

    /** Strips the most common markdown so release notes read as plain text in the card. */
    private static String plainNotes(String md) {
        String t = md.replaceAll("(?m)^\\s*#+\\s*", "").replaceAll("[*_`]", "").replaceAll("\n{3,}", "\n\n").trim();
        return t.length() > 600 ? t.substring(0, 600).trim() + "\u2026" : t;
    }

    private void bindGithub(Integer stars, Integer commits, String site, Release rel, boolean relFailed) {
        if (githubStarsValue == null) return;
        if (stars == null && commits == null && rel == null && relFailed) githubDueAt = System.currentTimeMillis() + FEED_RETRY_MS;
        if (stars != null) githubStarsValue.setText(String.format(Locale.getDefault(), "%,d", stars));
        if (commits != null) githubCommitsValue.setText(String.format(Locale.getDefault(), "%,d", commits));
        if (site != null) githubSite = site;
        bindRelease(rel, relFailed);
    }

    private void bindRelease(Release r, boolean failed) {
        if (githubReleaseTitle == null) return;
        if (r == null && failed && githubRelease != null) return; // keep the last good release on a failed refresh
        githubRelease = r;
        if (r == null) {
            githubReleaseTitle.setText(failed ? R.string.home_release_failed : R.string.home_release_none);
            githubReleaseMeta.setVisibility(View.GONE);
            githubReleaseNotes.setVisibility(View.GONE);
            githubReleaseOpen.setVisibility(View.GONE);
            return;
        }
        githubReleaseTitle.setText(r.name.isEmpty() ? r.tag : r.name);
        githubReleaseMeta.setText(getString(R.string.home_release_meta, r.tag, r.date));
        githubReleaseMeta.setVisibility(View.VISIBLE);
        githubReleaseNotes.setText(r.notes);
        githubReleaseNotes.setVisibility(r.notes.isEmpty() ? View.GONE : View.VISIBLE);
        githubReleaseOpen.setVisibility(View.VISIBLE);
        githubReleaseOpen.setOnClickListener(x -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url))));
    }

    private void exploreNews() {
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        TextView loading = new TextView(requireContext());
        loading.setText(R.string.home_news_loading);
        loading.setTextAppearance(R.style.TextAppearance_Voidstrap_Caption);
        list.addView(loading);
        MaterialButton all = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        all.setText(R.string.home_news_open_forum);
        all.setIconResource(R.drawable.ic_globe);
        all.setOnClickListener(x -> Ui.openWeb(requireContext(), HomeFeed.NEWS_PAGE));
        Ui.surface(host(), R.string.home_news_all_title, R.string.home_news_all_body, list).show();
        Context app = requireContext().getApplicationContext();
        store.work.execute(() -> {
            List<HomeFeed.News> news = null;
            try {
                news = HomeFeed.allNews(app);
            } catch (IOException | RuntimeException ignored) {
            }
            List<HomeFeed.News> n = news;
            store.main.post(() -> {
                if (!isAdded()) return;
                list.removeAllViews();
                if (n == null || n.isEmpty()) {
                    loading.setText(R.string.home_news_failed);
                    list.addView(loading);
                }
                if (n != null) for (HomeFeed.News item : n) {
                    Row row = Row.inflate(list);
                    String detail = item.tag.isEmpty() ? newsDate(item.created) : getString(R.string.pair, item.tag, newsDate(item.created));
                    row.set(R.drawable.ic_megaphone, item.title, detail);
                    if (!item.image.isEmpty()) row.showImage(HomeFeed.thumbnail(item.image, 160), R.drawable.ic_megaphone);
                    row.title.setMaxLines(2);
                    row.chevron.setVisibility(View.VISIBLE);
                    row.view.setOnClickListener(x -> Ui.openWeb(requireContext(), item.url));
                    list.addView(row.view);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(requireContext(), 12);
                list.addView(all, lp);
            });
        });
    }
}
