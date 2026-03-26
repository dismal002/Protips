package com.dismal.protips;

/**
 * Simple struct class to hold the data for one rss item --
 * title, link, description.
 */
public class RssItem {
    private String mTitle;
    private String mLink;
    private String mDescription;
    
    public RssItem() {
        mTitle = "";
        mLink = "";
        mDescription = "";
    }
    
    public RssItem(String title, String link, String description) {
        mTitle = title;
        mLink = link;
        mDescription = description;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public String getLink() {
        return mLink;
    }

    public void setLink(String link) {
        mLink = link;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }
}
