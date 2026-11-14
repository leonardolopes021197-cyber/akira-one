<?php
require_once __DIR__ . '/bootstrap.php';
require_login();
$pageTitle='Visão Geral';
$pdo=db();
$metrics=[
 'Ativações'=>(int)$pdo->query("SELECT COUNT(*) FROM activations WHERE is_active=1")->fetchColumn(),
 'Dispositivos online'=>(int)$pdo->query("SELECT COUNT(*) FROM devices WHERE last_seen >= datetime('now','-10 minutes')")->fetchColumn(),
 'Servidores'=>(int)$pdo->query("SELECT COUNT(*) FROM servers WHERE is_active=1")->fetchColumn(),
 'Jogos hoje'=>(int)$pdo->query("SELECT COUNT(*) FROM games WHERE date(starts_at)=date('now','localtime')")->fetchColumn(),
];
$recent=$pdo->query("SELECT d.*, (SELECT COUNT(*) FROM activations a WHERE a.device_id=d.device_id AND a.is_active=1) activations FROM devices d ORDER BY COALESCE(last_seen,created_at) DESC LIMIT 8")->fetchAll();
$games=$pdo->query("SELECT * FROM games WHERE date(starts_at)=date('now','localtime') ORDER BY starts_at LIMIT 8")->fetchAll();
include __DIR__.'/partials/header.php';
?>
<div class="grid">
<?php $accents=['#ff402f','#22e39a','#00b8ff','#8a4dff']; $i=0; foreach($metrics as $label=>$value): ?>
  <div class="metric" style="--accent:<?=$accents[$i++]?>"><small><?=h($label)?></small><b><?=$value?></b></div>
<?php endforeach?>
</div>
<div class="hero">
  <section class="card">
    <img class="hero-logo" src="assets/akira-brand.png" alt="Akira">
    <h2>App Android + painel administrativo Akira</h2>
    <p>Controle servidores DNS, ativações, dispositivos reconhecidos e a área de Jogos do Dia em uma única interface. O aplicativo consulta a ativação vinculada ao dispositivo e carrega a lista configurada pelo painel.</p>
    <div class="actions"><a class="btn" href="activations.php">Nova ativação</a><a class="btn blue" href="games.php">Gerenciar jogos</a></div>
  </section>
  <section class="card">
    <div class="section-title"><h2>Jogos de hoje</h2><a class="muted" href="games.php">ver todos</a></div>
    <?php if(!$games):?><div class="empty">Nenhuma partida cadastrada para hoje.</div><?php endif?>
    <?php foreach($games as $g):?><div class="mini-game"><div><b><?=h($g['home_team'])?> × <?=h($g['away_team'])?></b><div class="muted"><?=h($g['competition'])?> • <?=h(date('H:i',strtotime($g['starts_at'])))?></div></div><div class="score"><?=is_null($g['home_score'])?'—':(int)$g['home_score']?> : <?=is_null($g['away_score'])?'—':(int)$g['away_score']?></div></div><?php endforeach?>
  </section>
</div>
<section class="card"><div class="section-title"><h2>Dispositivos recentes</h2><a class="muted" href="devices.php">abrir lista</a></div><div class="table-wrap"><table><thead><tr><th>ID</th><th>Dispositivo</th><th>App</th><th>Última conexão</th><th>Ativações</th></tr></thead><tbody>
<?php if(!$recent):?><tr><td colspan="5" class="empty">Os dispositivos aparecerão aqui após o primeiro acesso do app.</td></tr><?php endif?>
<?php foreach($recent as $d):?><tr><td><span class="code"><?=h($d['device_id'])?></span></td><td><?=h($d['device_name'] ?: $d['model'])?></td><td><?=h($d['app_version'])?></td><td><?=h($d['last_seen'] ?: 'Nunca')?></td><td><?= (int)$d['activations']?></td></tr><?php endforeach?>
</tbody></table></div></section>
<?php include __DIR__.'/partials/footer.php'; ?>
