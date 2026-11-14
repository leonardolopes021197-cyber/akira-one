package com.akira.one;

import org.json.JSONObject;

public class ContentItem {
    public final String id, name, icon, type, extension, meta, categoryId, sourceUrl;

    public ContentItem(String id,String name,String icon,String type,String extension,String meta){
        this(id,name,icon,type,extension,meta,"","");
    }

    public ContentItem(String id,String name,String icon,String type,String extension,String meta,String categoryId){
        this(id,name,icon,type,extension,meta,categoryId,"");
    }

    public ContentItem(String id,String name,String icon,String type,String extension,String meta,String categoryId,String sourceUrl){
        this.id=id==null?"":id;
        this.name=name==null?"Sem nome":name;
        this.icon=icon==null?"":icon;
        this.type=type==null?"":type;
        this.extension=extension==null?"":extension;
        this.meta=meta==null?"":meta;
        this.categoryId=categoryId==null?"":categoryId;
        this.sourceUrl=sourceUrl==null?"":sourceUrl.trim();
    }

    public String key(){ return type+":"+id; }

    public JSONObject toJson(){
        JSONObject o=new JSONObject();
        try{
            o.put("id",id);
            o.put("name",name);
            o.put("icon",icon);
            o.put("type",type);
            o.put("extension",extension);
            o.put("meta",meta);
            o.put("category_id",categoryId);
            o.put("source_url",sourceUrl);
        }catch(Exception ignored){}
        return o;
    }

    public static ContentItem fromJson(JSONObject o){
        return new ContentItem(
                o.optString("id"),
                o.optString("name"),
                o.optString("icon"),
                o.optString("type"),
                o.optString("extension"),
                o.optString("meta"),
                o.optString("category_id"),
                o.optString("source_url")
        );
    }
}
