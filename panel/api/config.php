<?php
require_once __DIR__.'/../bootstrap.php';
$device=device_auth();$deviceId=(string)$device['device_id'];$s=current_settings();
$st=db()->prepare('SELECT a.*,sv.name server_name,sv.base_url FROM activations a JOIN servers sv ON sv.id=a.server_id WHERE a.device_id=? AND a.is_active=1 AND sv.is_active=1 ORDER BY a.is_primary DESC,a.id DESC');$st->execute([$deviceId]);$rows=$st->fetchAll();
$activations=[];$primary=null;
foreach($rows as $a){$expired=!empty($a['expires_at'])&&strtotime((string)$a['expires_at'].' 23:59:59')<time();if($expired)continue;$item=['id'=>(int)$a['id'],'list_name'=>$a['list_name'],'server_name'=>$a['server_name'],'base_url'=>rtrim((string)$a['base_url'],'/'),'username'=>decrypt_secret((string)$a['username_enc']),'password'=>decrypt_secret((string)$a['password_enc']),'expires_at'=>$a['expires_at']??'','is_primary'=>(bool)$a['is_primary']];$activations[]=$item;if($primary===null||$a['is_primary'])$primary=$item;}
json_response([
 'ok'=>true,'active'=>$primary!==null,
 'app'=>['name'=>$s['app_name'],'logo_url'=>$s['logo_url'],'background_url'=>$s['background_url'],'support_text'=>$s['support_text']],
 'activation'=>$primary,'activations'=>$activations,
 'update'=>['version'=>$s['latest_version']??'','version_code'=>(int)($s['latest_version_code']??0),'apk_url'=>$s['latest_apk_url']??'','message'=>$s['update_message']??''],
 'games_url'=>base_path_url('api/games.php')
]);
