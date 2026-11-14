<?php
require_once __DIR__.'/../bootstrap.php';
if($_SERVER['REQUEST_METHOD']!=='POST') json_response(['ok'=>false,'error'=>'method_not_allowed'],405);
$d=json_input();$deviceId=trim((string)($d['device_id']??''));
if($deviceId===''||strlen($deviceId)>100) json_response(['ok'=>false,'error'=>'invalid_device_id'],422);
$st=db()->prepare('SELECT * FROM devices WHERE device_id=?');$st->execute([$deviceId]);$row=$st->fetch();
if($row){$token=(string)$row['device_token'];db()->prepare('UPDATE devices SET device_name=?,model=?,app_version=?,last_ip=?,last_seen=CURRENT_TIMESTAMP WHERE device_id=?')->execute([substr((string)($d['device_name']??''),0,100),substr((string)($d['model']??''),0,100),substr((string)($d['app_version']??''),0,50),client_ip(),$deviceId]);}
else{$token=bin2hex(random_bytes(24));db()->prepare('INSERT INTO devices(device_id,device_token,device_name,model,app_version,last_ip,last_seen) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)')->execute([$deviceId,$token,substr((string)($d['device_name']??''),0,100),substr((string)($d['model']??''),0,100),substr((string)($d['app_version']??''),0,50),client_ip()]);}
json_response(['ok'=>true,'device_id'=>$deviceId,'device_token'=>$token]);
