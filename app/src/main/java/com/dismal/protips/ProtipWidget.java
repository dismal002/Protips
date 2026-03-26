/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dismal.protips;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.RemoteViews;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mister Widget appears on your home screen to provide helpful tips. */
public class ProtipWidget extends AppWidgetProvider {
    public static final String ACTION_NEXT_TIP = "com.dismal.protips.NEXT_TIP";
    public static final String ACTION_POKE = "com.dismal.protips.HEE_HEE";

    public static final String EXTRA_TIMES = "times";

    public static final String PREFS_NAME = "Protips";
    public static final String PREFS_TIP_NUMBER = "widget_tip";
    public static final String PREFS_TIP_SET = "widget_tip_set";
    public static final String PREFS_MODE = "widget_mode";
    public static final String PREFS_RSS_ITEMS = "rss_items";
    public static final String PREFS_RSS_URL = "rss_url";

    public static final int MODE_TIPS = 0;
    public static final int MODE_RSS = 1;

    public static final String DEFAULT_RSS_URL = "https://blog.chromium.org/rss.xml";

    private static final Pattern sNewlineRegex = Pattern.compile(" *\\n *");
    private static final Pattern sDrawableRegex = Pattern.compile(" *@(drawable/[a-z0-9_]+) *");

    private static Handler mAsyncHandler;
    static {
        HandlerThread thr = new HandlerThread("ProtipWidget async");
        thr.start();
        mAsyncHandler = new Handler(thr.getLooper());
    }
    
    // initial appearance: eyes closed, no bubble
    private int mIconRes = R.drawable.droidman_open;
    private int mMessage = 0;
    private int mTipSet = 0;

    private AppWidgetManager mWidgetManager = null;
    private int[] mWidgetIds;
    private Context mContext;

    private CharSequence[] mTips;
    private static List<RssItem> sRssItems;
    private int mMode = MODE_TIPS;
    private List<String> mRssUrls;

    public static List<RssItem> getRssItems() {
        return sRssItems;
    }

    private void setup(Context context) {
        mContext = context;
        mWidgetManager = AppWidgetManager.getInstance(context);
        mWidgetIds = mWidgetManager.getAppWidgetIds(new ComponentName(context, ProtipWidget.class));

        SharedPreferences pref = context.getSharedPreferences(PREFS_NAME, 0);
        mMode = pref.getInt(PREFS_MODE, MODE_TIPS);
        
        mRssUrls = new ArrayList<>();
        if (pref.getBoolean("use_default_feed", true)) {
            mRssUrls.add(DEFAULT_RSS_URL);
        }
        Set<String> customFeeds = pref.getStringSet("custom_feeds", new HashSet<String>());
        mRssUrls.addAll(customFeeds);
        
        if (mMode == MODE_TIPS) {
            setupTips(pref);
        } else {
            setupRss(pref);
        }
    }

    private void setupTips(SharedPreferences pref) {
        mMessage = pref.getInt(PREFS_TIP_NUMBER, 0);
        mTipSet = pref.getInt(PREFS_TIP_SET, 0);
        mTips = mContext.getResources().getTextArray(mTipSet == 1 ? R.array.tips2 : R.array.tips);

        if (mTips != null) {
            if (mMessage >= mTips.length) mMessage = 0;
        } else {
            mMessage = -1;
        }
    }

    private void setupRss(SharedPreferences pref) {
        mMessage = pref.getInt(PREFS_TIP_NUMBER, 0);
        // In a real app, we'd persist RSS items or fetch them.
        // For now, let's assume they are fetched asynchronously.
        if (sRssItems == null) {
            fetchRss();
        }
    }

    public void goodmorning() {
        mMessage = -1;
        try {
            setIcon(R.drawable.droidman_down_closed);
            Thread.sleep(500);
            setIcon(R.drawable.droidman_down_open);
            Thread.sleep(200);
            setIcon(R.drawable.droidman_down_closed);
            Thread.sleep(100);
            setIcon(R.drawable.droidman_down_open);
            Thread.sleep(600);
        } catch (InterruptedException ex) {
        }
        mMessage = 0;
        mIconRes = R.drawable.droidman_open;
        refresh();
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult result = goAsync();
        Runnable worker = new Runnable() {
            @Override
            public void run() {
                onReceiveAsync(context, intent);
                result.finish();
            }
        };
        mAsyncHandler.post(worker);
    }
    
    void onReceiveAsync(Context context, Intent intent) {
        setup(context);

        if (intent.getAction().equals(ACTION_NEXT_TIP)) {
            if (mMode == MODE_TIPS) {
                mMessage = getNextMessageIndex();
                SharedPreferences.Editor pref = context.getSharedPreferences(PREFS_NAME, 0).edit();
                pref.putInt(PREFS_TIP_NUMBER, mMessage);
                pref.apply();
                refresh();
            } else {
                // In RSS mode, "Next" can just refresh the feed or we can skip it
                fetchRss();
            }
        } else if (intent.getAction().equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED)) {
            goodmorning();
        } else if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")) {
            Log.d("Protips", "ACHIEVEMENT UNLOCKED");
            mTipSet = 1 - mTipSet;
            mMessage = 0;

            SharedPreferences.Editor pref = context.getSharedPreferences(PREFS_NAME, 0).edit();
            pref.putInt(PREFS_TIP_NUMBER, mMessage);
            pref.putInt(PREFS_TIP_SET, mTipSet);
            pref.apply();

            mContext.startActivity(
                new Intent(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .addCategory(Intent.CATEGORY_HOME));
            
            final Intent bcast = new Intent(context, ProtipWidget.class);
            bcast.setAction(ACTION_POKE);
            bcast.putExtra(EXTRA_TIMES, 3);
            mContext.sendBroadcast(bcast);
        } else if (intent.getAction().equals(ACTION_POKE)) {
            // Use poke to toggle mode
            mMode = 1 - mMode;
            SharedPreferences.Editor pref = context.getSharedPreferences(PREFS_NAME, 0).edit();
            pref.putInt(PREFS_MODE, mMode);
            pref.putInt(PREFS_TIP_NUMBER, 0); // Reset index
            pref.apply();
            
            if (mMode == MODE_RSS) {
                fetchRss();
            }
            refresh();
            blink(1);
        } else {
            mIconRes = R.drawable.droidman_open;
            if (mMode == MODE_RSS) {
                fetchRss();
            }
            refresh();
        }
    }

    private void fetchRss() {
        mAsyncHandler.post(new Runnable() {
            @Override
            public void run() {
                List<RssItem> allItems = new ArrayList<>();
                for (String rssUrl : mRssUrls) {
                    try {
                        URL url = new URL(rssUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(10000);
                        conn.connect();
                        InputStream in = conn.getInputStream();
                        List<RssItem> items = parseRss(in);
                        if (items != null) {
                            allItems.addAll(items);
                        }
                    } catch (Exception e) {
                        Log.e("Protips", "Error fetching RSS from " + rssUrl, e);
                    }
                }
                
                if (!allItems.isEmpty()) {
                    sRssItems = allItems;
                } else {
                    sRssItems = null;
                }
                mWidgetManager.notifyAppWidgetViewDataChanged(mWidgetIds, R.id.rss_list);
                refresh();
            }
        });
    }

    private List<RssItem> parseRss(InputStream in) throws Exception {
        XmlPullParser xpp = Xml.newPullParser();
        xpp.setInput(in, null);
        int eventType = xpp.getEventType();
        List<RssItem> items = new ArrayList<>();
        RssItem currentItem = null;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = xpp.getName();
                if (tag.equals("item")) {
                    currentItem = new RssItem();
                } else if (currentItem != null) {
                    if (tag.equals("title")) {
                        currentItem.setTitle(xpp.nextText());
                    } else if (tag.equals("link")) {
                        currentItem.setLink(xpp.nextText());
                    } else if (tag.equals("description")) {
                        currentItem.setDescription(xpp.nextText());
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (xpp.getName().equals("item") && currentItem != null) {
                    items.add(currentItem);
                    currentItem = null;
                }
            }
            eventType = xpp.next();
        }
        return items;
    }

    private void refresh() {
        RemoteViews rv = buildUpdate(mContext);
        for (int i : mWidgetIds) {
            mWidgetManager.updateAppWidget(i, rv);
        }
    }

    private void setIcon(int resId) {
        mIconRes = resId;
        refresh();
    }

    private int getNextMessageIndex() {
        int length = (mMode == MODE_TIPS) ? mTips.length : (sRssItems != null ? sRssItems.size() : 0);
        if (length == 0) return 0;
        return (mMessage + 1) % length;
    }

    private void blink(int blinks) {
        // don't blink if no bubble showing or if goodmorning() is happening
        if (mMessage < 0) return;

        setIcon(R.drawable.droidman_closed);
        try {
            Thread.sleep(100);
            while (0<--blinks) {
                setIcon(R.drawable.droidman_open);
                Thread.sleep(200);
                setIcon(R.drawable.droidman_closed);
                Thread.sleep(100);
            }
        } catch (InterruptedException ex) { }
        setIcon(R.drawable.droidman_open);
    }

    public RemoteViews buildUpdate(Context context) {
        RemoteViews updateViews = new RemoteViews(
            context.getPackageName(), R.layout.widget);

        // Action for tap on bubble (container)
        // In RSS mode, we use a ListView, so this might not be needed for items,
        // but can be used for the bubble background.
        Intent bcast = new Intent(context, ProtipWidget.class);
        bcast.setAction(ACTION_NEXT_TIP);
        PendingIntent pending = PendingIntent.getBroadcast(
            context, 0, bcast, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        updateViews.setOnClickPendingIntent(R.id.tip_bubble, pending);

        // Action for tap on android
        bcast = new Intent(context, ProtipWidget.class);
        bcast.setAction(ACTION_POKE);
        bcast.putExtra(EXTRA_TIMES, 1);
        pending = PendingIntent.getBroadcast(
            context, 0, bcast, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        updateViews.setOnClickPendingIntent(R.id.bugdroid, pending);

        // ListView click template
        Intent clickIntent = new Intent(Intent.ACTION_VIEW);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(context, 0, clickIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE); // Click item needs to be mutable to be filled in
        updateViews.setPendingIntentTemplate(R.id.rss_list, clickPendingIntent);

        // Tip bubble text
        if (mMode == MODE_TIPS) {
            updateTipsUI(updateViews);
        } else {
            updateRssUI(updateViews, context);
        }

        updateViews.setImageViewResource(R.id.bugdroid, mIconRes);

        return updateViews;
    }

    private void updateTipsUI(RemoteViews updateViews) {
        if (mMessage >= 0 && mTips != null && mMessage < mTips.length) {
            String[] parts = sNewlineRegex.split(mTips[mMessage], 2);
            String title = parts[0];
            String text = parts.length > 1 ? parts[1] : "";

            // Look for a callout graphic referenced in the text
            Matcher m = sDrawableRegex.matcher(text);
            if (m.find()) {
                String imageName = m.group(1);
                int resId = mContext.getResources().getIdentifier(
                    imageName, null, mContext.getPackageName());
                updateViews.setImageViewResource(R.id.tip_callout, resId);
                updateViews.setViewVisibility(R.id.tip_callout, View.VISIBLE);
                text = m.replaceFirst("");
            } else {
                updateViews.setImageViewResource(R.id.tip_callout, 0);
                updateViews.setViewVisibility(R.id.tip_callout, View.GONE);
            }

            updateViews.setTextViewText(R.id.tip_message, text);
            updateViews.setTextViewText(R.id.tip_header, title);
            updateViews.setTextViewText(R.id.tip_footer, 
                mContext.getResources().getString(
                    R.string.pager_footer,
                    (1+mMessage), mTips.length));
            updateViews.setViewVisibility(R.id.tip_bubble, View.VISIBLE);
            updateViews.setViewVisibility(R.id.rss_list, View.GONE);
        } else {
            updateViews.setViewVisibility(R.id.tip_bubble, View.INVISIBLE);
        }
    }

    private void updateRssUI(RemoteViews updateViews, Context context) {
        if (sRssItems != null && !sRssItems.isEmpty()) {
            updateViews.setTextViewText(R.id.tip_header, "RSS Feed (Tap for Settings)");
            
            // Open settings on header click
            Intent settingsIntent = new Intent(context, SettingsActivity.class);
            PendingIntent settingsPending = PendingIntent.getActivity(context, 0, settingsIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            updateViews.setOnClickPendingIntent(R.id.tip_header, settingsPending);

            updateViews.setTextViewText(R.id.tip_footer, "Total articles: " + sRssItems.size());
            updateViews.setViewVisibility(R.id.tip_message, View.GONE);
            updateViews.setViewVisibility(R.id.tip_callout, View.GONE);
            updateViews.setViewVisibility(R.id.tip_bubble, View.VISIBLE);
            
            updateViews.setViewVisibility(R.id.rss_list, View.VISIBLE);
            
            Intent serviceIntent = new Intent(context, RssRemoteViewsService.class);
            updateViews.setRemoteAdapter(R.id.rss_list, serviceIntent);
        } else {
            updateViews.setTextViewText(R.id.tip_header, "Loading RSS (Tap for Settings)");
            
            // Open settings even when loading
            Intent settingsIntent = new Intent(context, SettingsActivity.class);
            PendingIntent settingsPending = PendingIntent.getActivity(context, 0, settingsIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            updateViews.setOnClickPendingIntent(R.id.tip_header, settingsPending);

            updateViews.setTextViewText(R.id.tip_message, "Fetching from " + mRssUrls.size() + " feeds...");
            updateViews.setViewVisibility(R.id.tip_message, View.VISIBLE);
            updateViews.setViewVisibility(R.id.tip_bubble, View.VISIBLE);
            updateViews.setViewVisibility(R.id.rss_list, View.GONE);
        }
    }
}
