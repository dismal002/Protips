package com.dismal.protips;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class RssRemoteViewsService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RssRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class RssRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context mContext;
    private List<RssItem> mItems = new ArrayList<>();

    public RssRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
    }

    @Override
    public void onCreate() {
        // In a real app, we might initialize data here.
    }

    @Override
    public void onDataSetChanged() {
        // This is called when notifyAppWidgetViewDataChanged is called.
        // We can access the singleton or static list from ProtipWidget.
        mItems = ProtipWidget.getRssItems();
    }

    @Override
    public void onDestroy() {
        mItems.clear();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position >= mItems.size()) return null;

        RssItem item = mItems.get(position);
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.rss_item);
        rv.setTextViewText(R.id.rss_item_title, item.getTitle());

        // Create a fill-in intent for the item click
        Intent fillInIntent = new Intent();
        fillInIntent.setData(android.net.Uri.parse(item.getLink().toString()));
        rv.setOnClickFillInIntent(R.id.rss_item_container, fillInIntent);

        return rv;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
