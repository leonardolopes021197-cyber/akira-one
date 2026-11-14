<?php
require_once __DIR__ . '/bootstrap.php';
if (is_installed()) { header('Location: login.php'); exit; }
$error = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = trim((string)($_POST['username'] ?? 'admin'));
    $password = (string)($_POST['password'] ?? '');
    if (strlen($username) < 3 || strlen($password) < 8) {
        $error = 'Use um usuário com 3+ caracteres e senha com pelo menos 8 caracteres.';
    } else {
        $st = db()->prepare('INSERT INTO admins(username,password_hash) VALUES(?,?)');
        $st->execute([$username, password_hash($password, PASSWORD_DEFAULT)]);
        app_secret();
        header('Location: login.php?installed=1'); exit;
    }
}
?>
<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Instalar Akira Manager</title><link rel="stylesheet" href="assets/style.css"></head><body>
<div class="auth-wrap"><div class="auth-card"><img src="assets/akira-brand.png" alt="Akira"><h1>Instalação inicial</h1><p>Crie o primeiro administrador do painel. O banco SQLite e a chave de criptografia serão preparados automaticamente.</p><?php if($error):?><div class="notice"><?=h($error)?></div><?php endif?><form method="post" class="stack"><label>Usuário<input name="username" value="admin" required></label><label>Senha<input type="password" name="password" minlength="8" required></label><button class="btn">Instalar painel</button></form></div></div></body></html>
