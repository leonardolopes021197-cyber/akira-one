package com.akira.one;

import org.json.JSONObject;

public class ListProfile {
    public final int id; public final String name, serverName, baseUrl, username, password, expiresAt; public final boolean primary;
    public ListProfile(int id,String name,String serverName,String baseUrl,String username,String password,String expiresAt,boolean primary){
        this.id=id;this.name=name;this.serverName=serverName;this.baseUrl=baseUrl;this.username=username;this.password=password;this.expiresAt=expiresAt;this.primary=primary;
    }
    public static ListProfile fromJson(JSONObject o){return new ListProfile(o.optInt("id"),o.optString("list_name","Principal"),o.optString("server_name"),o.optString("base_url"),o.optString("username"),o.optString("password"),o.optString("expires_at"),o.optBoolean("is_primary",false));}
}
