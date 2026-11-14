<?php
require_once __DIR__.'/../bootstrap.php';
device_auth();$date=trim((string)($_GET['date']??date('Y-m-d')));if(!preg_match('/^\d{4}-\d{2}-\d{2}$/',$date)) json_response(['ok'=>false,'error'=>'invalid_date'],422);
$st=db()->prepare('SELECT id,starts_at,competition,home_team,away_team,home_score,away_score,status,channel_keywords,stream_id FROM games WHERE date(starts_at)=? ORDER BY starts_at');$st->execute([$date]);$rows=$st->fetchAll();
foreach($rows as &$r){$r['id']=(int)$r['id'];$r['home_score']=is_null($r['home_score'])?null:(int)$r['home_score'];$r['away_score']=is_null($r['away_score'])?null:(int)$r['away_score'];}
json_response(['ok'=>true,'date'=>$date,'games'=>$rows]);
