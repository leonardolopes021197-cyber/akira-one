<?php
require_once __DIR__ . '/../bootstrap.php';
require_login();
$settings = current_settings();
$pageTitle = $pageTitle ?? 'Akira Manager';
$current = basename($_SERVER['PHP_SELF'] ?? 'index.php');
?>
<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title><?=h($pageTitle)?> • <?=h($settings['panel_name'] ?? 'Akira Manager')?></title>
<link rel="stylesheet" href="assets/style.css">
</head>
<body>
<div class="shell">
  <aside class="sidebar">
    <a class="brand" href="index.php"><img src="assets/akira-brand.png" alt="Akira"></a>
    <nav>
      <a class="<?=$current==='index.php'?'active':''?>" href="index.php">⌂ <span>Visão geral</span></a>
      <a class="<?=$current==='servers.php'?'active':''?>" href="servers.php">◉ <span>Servidores DNS</span></a>
      <a class="<?=$current==='activations.php'?'active':''?>" href="activations.php">◆ <span>Ativações</span></a>
      <a class="<?=$current==='devices.php'?'active':''?>" href="devices.php">▣ <span>Dispositivos</span></a>
      <a class="<?=$current==='games.php'?'active':''?>" href="games.php">⚽ <span>Jogos do Dia</span></a>
      <a class="<?=$current==='settings.php'?'active':''?>" href="settings.php">⚙ <span>Configurações</span></a>
      <a class="<?=$current==='logs.php'?'active':''?>" href="logs.php">≡ <span>Logs / Auditoria</span></a>
      <a href="public_activation.php" target="_blank">↗ <span>Ativação pública</span></a>
    </nav>
    <div class="side-footer"><a href="logout.php">Sair</a></div>
  </aside>
  <main class="content">
    <header class="topbar">
      <div><span class="eyebrow">AKIRA CONTROL</span><h1><?=h($pageTitle)?></h1></div>
      <div class="status-pill"><span></span> Sistema online</div>
    </header>
