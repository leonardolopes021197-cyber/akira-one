<?php
declare(strict_types=1);

session_start();
date_default_timezone_set('America/Sao_Paulo');

const APP_ROOT = __DIR__;
const DATA_DIR = APP_ROOT . '/data';
const DB_FILE = DATA_DIR . '/akira.sqlite';
const SECRET_FILE = DATA_DIR . '/app.secret';

if (!is_dir(DATA_DIR)) mkdir(DATA_DIR, 0750, true);

function db(): PDO {
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;
    $pdo = new PDO('sqlite:' . DB_FILE, null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $pdo->exec('PRAGMA foreign_keys = ON;');
    $pdo->exec('PRAGMA journal_mode = WAL;');
    return $pdo;
}

function ensure_column(string $table, string $column, string $definition): void {
    $cols = db()->query('PRAGMA table_info(' . $table . ')')->fetchAll();
    foreach ($cols as $c) if (($c['name'] ?? '') === $column) return;
    db()->exec('ALTER TABLE ' . $table . ' ADD COLUMN ' . $column . ' ' . $definition);
}

function ensure_schema(): void {
    $sql = <<<'SQL'
CREATE TABLE IF NOT EXISTS admins (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  panel_name TEXT NOT NULL DEFAULT 'Akira Manager',
  app_name TEXT NOT NULL DEFAULT 'Akira TV',
  logo_url TEXT NOT NULL DEFAULT '',
  background_url TEXT NOT NULL DEFAULT '',
  support_text TEXT NOT NULL DEFAULT 'Suporte Akira',
  public_password_hash TEXT NOT NULL DEFAULT '',
  sports_api_url TEXT NOT NULL DEFAULT '',
  sports_api_key_enc TEXT NOT NULL DEFAULT '',
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS servers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  base_url TEXT NOT NULL,
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS devices (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL UNIQUE,
  device_token TEXT NOT NULL,
  device_name TEXT NOT NULL DEFAULT '',
  model TEXT NOT NULL DEFAULT '',
  app_version TEXT NOT NULL DEFAULT '',
  last_ip TEXT NOT NULL DEFAULT '',
  last_seen TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS activations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  server_id INTEGER NOT NULL,
  list_name TEXT NOT NULL DEFAULT 'Principal',
  username_enc TEXT NOT NULL,
  password_enc TEXT NOT NULL,
  expires_at TEXT,
  is_primary INTEGER NOT NULL DEFAULT 1,
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_activations_device ON activations(device_id);
CREATE TABLE IF NOT EXISTS games (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  external_id TEXT NOT NULL DEFAULT '',
  starts_at TEXT NOT NULL,
  competition TEXT NOT NULL DEFAULT '',
  home_team TEXT NOT NULL,
  away_team TEXT NOT NULL,
  home_score INTEGER,
  away_score INTEGER,
  status TEXT NOT NULL DEFAULT 'scheduled',
  channel_keywords TEXT NOT NULL DEFAULT '',
  stream_id TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_games_starts_at ON games(starts_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_games_external ON games(external_id) WHERE external_id <> '';
CREATE TABLE IF NOT EXISTS audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  admin_id INTEGER,
  admin_user TEXT NOT NULL DEFAULT '',
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL DEFAULT '',
  entity_id TEXT NOT NULL DEFAULT '',
  details TEXT NOT NULL DEFAULT '',
  ip TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at);
SQL;
    db()->exec($sql);
    db()->exec("INSERT OR IGNORE INTO settings (id) VALUES (1)");
    ensure_column('settings','sports_provider',"TEXT NOT NULL DEFAULT 'generic'");
    ensure_column('settings','latest_apk_url',"TEXT NOT NULL DEFAULT ''");
    ensure_column('settings','latest_version',"TEXT NOT NULL DEFAULT ''");
    ensure_column('settings','latest_version_code',"INTEGER NOT NULL DEFAULT 0");
    ensure_column('settings','update_message',"TEXT NOT NULL DEFAULT 'Nova versão da Akira TV disponível.'");
}
ensure_schema();

function app_secret(): string {
    if (!file_exists(SECRET_FILE)) {
        file_put_contents(SECRET_FILE, base64_encode(random_bytes(32)), LOCK_EX);
        @chmod(SECRET_FILE, 0600);
    }
    $raw = trim((string)file_get_contents(SECRET_FILE));
    $key = base64_decode($raw, true);
    if ($key === false || strlen($key) < 32) throw new RuntimeException('Chave de aplicação inválida.');
    return substr($key, 0, 32);
}

function encrypt_secret(string $value): string {
    if ($value === '') return '';
    $iv = random_bytes(12); $tag = '';
    $cipher = openssl_encrypt($value, 'aes-256-gcm', app_secret(), OPENSSL_RAW_DATA, $iv, $tag);
    if ($cipher === false) throw new RuntimeException('Falha ao criptografar.');
    return base64_encode($iv . $tag . $cipher);
}
function decrypt_secret(string $value): string {
    if ($value === '') return '';
    $raw = base64_decode($value, true);
    if ($raw === false || strlen($raw) < 28) return '';
    $iv = substr($raw,0,12); $tag = substr($raw,12,16); $cipher = substr($raw,28);
    $plain = openssl_decrypt($cipher,'aes-256-gcm',app_secret(),OPENSSL_RAW_DATA,$iv,$tag);
    return $plain === false ? '' : $plain;
}

function h(?string $value): string { return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8'); }
function is_installed(): bool { return (int)db()->query('SELECT COUNT(*) FROM admins')->fetchColumn() > 0; }
function is_logged_in(): bool { return isset($_SESSION['admin_id']); }
function require_login(): void { if (!is_logged_in()) { header('Location: login.php'); exit; } }
function csrf_token(): string { if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(24)); return $_SESSION['csrf']; }
function verify_csrf(): void { $token=$_POST['csrf']??''; if(!is_string($token)||!hash_equals($_SESSION['csrf']??'',$token)){http_response_code(419);exit('Token CSRF inválido. Recarregue a página.');} }
function json_input(): array { $raw=file_get_contents('php://input');$data=json_decode($raw?:'{}',true);return is_array($data)?$data:[]; }
function json_response(array $data,int $status=200): never { http_response_code($status);header('Content-Type: application/json; charset=utf-8');header('Cache-Control: no-store');echo json_encode($data,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);exit; }
function client_ip(): string { return substr((string)($_SERVER['REMOTE_ADDR']??''),0,64); }
function current_settings(): array { $row=db()->query('SELECT * FROM settings WHERE id=1')->fetch();return $row?:[]; }
function audit_log(string $action,string $entityType='',string|int $entityId='',string $details=''): void {
    try{db()->prepare('INSERT INTO audit_logs(admin_id,admin_user,action,entity_type,entity_id,details,ip) VALUES(?,?,?,?,?,?,?)')->execute([$_SESSION['admin_id']??null,(string)($_SESSION['admin_user']??''),$action,$entityType,(string)$entityId,substr($details,0,2000),client_ip()]);}catch(Throwable $e){}
}
function base_path_url(string $path=''): string { $https=(!empty($_SERVER['HTTPS'])&&$_SERVER['HTTPS']!=='off');$scheme=$https?'https':'http';$host=$_SERVER['HTTP_HOST']??'localhost';$scriptDir=rtrim(str_replace('\\','/',dirname($_SERVER['SCRIPT_NAME']??'/')),'/');if(str_ends_with($scriptDir,'/api'))$scriptDir=dirname($scriptDir);return $scheme.'://'.$host.($scriptDir==='/'?'':$scriptDir).'/'.ltrim($path,'/'); }
function normalize_server_url(string $url): string { return rtrim(trim($url),'/'); }
function device_auth(): array {
    $deviceId=trim((string)($_GET['device_id']??$_POST['device_id']??''));$token=trim((string)($_SERVER['HTTP_X_DEVICE_TOKEN']??''));
    if($deviceId===''||$token==='')json_response(['ok'=>false,'error'=>'device_auth_required'],401);
    $st=db()->prepare('SELECT * FROM devices WHERE device_id=?');$st->execute([$deviceId]);$dev=$st->fetch();
    if(!$dev||!hash_equals((string)$dev['device_token'],$token))json_response(['ok'=>false,'error'=>'invalid_device_token'],403);
    db()->prepare('UPDATE devices SET last_seen=CURRENT_TIMESTAMP,last_ip=? WHERE device_id=?')->execute([client_ip(),$deviceId]);return $dev;
}
