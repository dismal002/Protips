package com.dismal.protips;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends Activity {
    private EditText mUrlEditText;
    private CheckBox mDefaultFeedCheckBox;
    private ListView mFeedsListView;
    private ArrayAdapter<String> mAdapter;
    private List<String> mCustomFeeds;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mPrefs = getSharedPreferences(ProtipWidget.PREFS_NAME, 0);
        mUrlEditText = findViewById(R.id.new_feed_url);
        mDefaultFeedCheckBox = findViewById(R.id.toggle_default_feed);
        mFeedsListView = findViewById(R.id.feeds_list);

        boolean useDefault = mPrefs.getBoolean("use_default_feed", true);
        mDefaultFeedCheckBox.setChecked(useDefault);

        int mode = mPrefs.getInt(ProtipWidget.PREFS_MODE, ProtipWidget.MODE_TIPS);
        if (mode == ProtipWidget.MODE_RSS) {
            ((android.widget.RadioButton)findViewById(R.id.radio_rss)).setChecked(true);
        } else {
            ((android.widget.RadioButton)findViewById(R.id.radio_tips)).setChecked(true);
        }

        android.widget.RadioGroup modeGroup = findViewById(R.id.mode_radio_group);
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int newMode = (checkedId == R.id.radio_rss) ? ProtipWidget.MODE_RSS : ProtipWidget.MODE_TIPS;
            mPrefs.edit().putInt(ProtipWidget.PREFS_MODE, newMode).apply();
        });

        Set<String> feedSet = mPrefs.getStringSet("custom_feeds", new HashSet<String>());
        mCustomFeeds = new ArrayList<>(feedSet);
        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mCustomFeeds);
        mFeedsListView.setAdapter(mAdapter);

        findViewById(R.id.add_feed_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = mUrlEditText.getText().toString().trim();
                if (!url.isEmpty() && !mCustomFeeds.contains(url)) {
                    mCustomFeeds.add(url);
                    mAdapter.notifyDataSetChanged();
                    mUrlEditText.setText("");
                    saveFeeds();
                } else {
                    Toast.makeText(SettingsActivity.this, "Invalid or duplicate URL", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mDefaultFeedCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPrefs.edit().putBoolean("use_default_feed", mDefaultFeedCheckBox.isChecked()).apply();
            }
        });
        
        // Remove feed on long click
        mFeedsListView.setOnItemLongClickListener((parent, view, position, id) -> {
            mCustomFeeds.remove(position);
            mAdapter.notifyDataSetChanged();
            saveFeeds();
            return true;
        });
    }

    private void saveFeeds() {
        mPrefs.edit().putStringSet("custom_feeds", new HashSet<>(mCustomFeeds)).apply();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Notify widget to refresh
        android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(this, ProtipWidget.class));
        android.content.Intent intent = new android.content.Intent(this, ProtipWidget.class);
        intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }
}
