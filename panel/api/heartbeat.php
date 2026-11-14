<?php
require_once __DIR__.'/../bootstrap.php';
$dev=device_auth();json_response(['ok'=>true,'server_time'=>date(DATE_ATOM),'device_id'=>$dev['device_id']]);
