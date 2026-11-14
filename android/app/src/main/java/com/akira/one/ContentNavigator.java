package com.akira.one;

import android.app.Activity;
import android.content.Intent;

import java.util.List;

public final class ContentNavigator {
    private ContentNavigator(){}

    public static void open(Activity a, ContentItem item){
        if("series".equals(item.type)){
            Intent i=new Intent(a, SeriesActivity.class);
            i.putExtra("series_id", item.id);
            i.putExtra("series_name", item.name);
            i.putExtra("series_cover", item.icon);
            i.putExtra("series_category_id", item.categoryId);
            a.startActivity(i);
            return;
        }

        Intent i=new Intent(a, PlayerActivity.class);
        i.putExtra("title", item.name);
        putContent(i, item);
        if("live".equals(item.type)){
            i.putExtra("type", "live");
            i.putExtra("stream_id", item.id);
            i.putExtra("direct_source", item.sourceUrl);
        }else{
            String url="episode".equals(item.type)
                    ?XtreamClient.seriesUrl(a,item.id,item.extension)
                    :XtreamClient.movieUrl(a,item.id,item.extension);
            i.putExtra("url",url);
        }
        a.startActivity(i);
    }

    public static void openLiveWithQualities(Activity a, ContentItem displayItem, List<ContentItem> variants, ContentItem selectedVariant) {
        if (a == null || displayItem == null) return;
        ContentItem selected = selectedVariant == null ? LiveQualityUtil.pickDefault(variants) : selectedVariant;
        if (selected == null) selected = displayItem;

        Intent i = new Intent(a, PlayerActivity.class);
        i.putExtra("title", displayItem.name);
        putContent(i, displayItem);
        i.putExtra("type", "live");
        i.putExtra("stream_id", selected.id);
        i.putExtra("direct_source", selected.sourceUrl);
        i.putExtra("live_selected_variant_id", selected.id);
        i.putExtra("live_quality_variants", LiveQualityUtil.variantsToJson(variants));
        a.startActivity(i);
    }

    public static void putContent(Intent i, ContentItem item) {
        if (i == null || item == null) return;
        i.putExtra("content_id", item.id);
        i.putExtra("content_name", item.name);
        i.putExtra("content_icon", item.icon);
        i.putExtra("content_type", item.type);
        i.putExtra("content_extension", item.extension);
        i.putExtra("content_meta", item.meta);
        i.putExtra("content_category_id", item.categoryId);
        i.putExtra("content_source_url", item.sourceUrl);
    }
}
