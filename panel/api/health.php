<?php
require_once __DIR__.'/../bootstrap.php';
json_response(['ok'=>true,'service'=>'Akira Manager','time'=>date(DATE_ATOM),'installed'=>is_installed()]);
