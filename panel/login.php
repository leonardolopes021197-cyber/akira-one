<?php
require_once __DIR__ . '/bootstrap.php';
if (!is_installed()) { header('Location: install.php'); exit; }
if (is_logged_in()) { header('Location: index.php'); exit; }
$error='';
if ($_SERVER['REQUEST_METHOD']==='POST') {
  $u=trim((string)($_POST['username']??'')); $p=(string)($_POST['password']??'');
  $st=db()->prepare('SELECT * FROM admins WHERE username=?'); $st->execute([$u]); $admin=$st->fetch();
  if ($admin && password_verify($p,(string)$admin['password_hash'])) { session_regenerate_id(true); $_SESSION['admin_id']=$admin['id']; $_SESSION['admin_user']=$admin['username']; audit_log('admin.login','admin',$admin['id']); header('Location:index.php'); exit; }
  $error='Usuário ou senha inválidos.';
}
?>
<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Entrar • Akira Manager</title><link rel="stylesheet" href="assets/style.css"></head><body><div class="auth-wrap"><div class="auth-card"><img src="assets/akira-brand.png" alt="Akira"><h1>Akira Manager</h1><p>Gerencie ativações, dispositivos, servidores e Jogos do Dia.</p><?php if(isset($_GET['installed'])):?><div class="flash">Painel instalado com sucesso.</div><?php endif?><?php if($error):?><div class="notice"><?=h($error)?></div><?php endif?><form method="post" class="stack"><label>Usuário<input name="username" autocomplete="username" required></label><label>Senha<input type="password" name="password" autocomplete="current-password" required></label><button class="btn">Entrar no painel</button></form></div></div></body></html>
