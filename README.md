
## Pré-carregamento do catálogo

A partir da versão 1.7.1, após o login o aplicativo começa a carregar TV ao Vivo, Filmes e Séries em segundo plano. Os catálogos ficam em memória e também são salvos no armazenamento interno do app. Ao abrir uma dessas áreas, o conteúdo em cache é exibido imediatamente e a atualização ocorre em segundo plano quando necessário.

# AKIRA ONE 1.2 — App Android TV + Painel PHP/SQLite

Projeto com identidade **Akira**, inspirado no fluxo funcional da referência enviada, mas com interface própria.

## Novidades desta versão

### Login inicial com usuário e senha
- Em uma instalação nova, o app abre diretamente na tela de **login Akira**.
- O cliente informa somente **usuário** e **senha**.
- O DNS/servidor não é exibido nem precisa ser digitado.
- Depois de uma autenticação válida, a sessão fica salva no aparelho.
- Em **Configurações > Trocar usuário / Sair**, é possível voltar à tela de login.

### 4 servidores Akira embutidos
O aplicativo testa automaticamente os servidores abaixo e usa o primeiro que aceitar as credenciais:

1. `http://akiratv.shop`
2. `http://streamix.site`
3. `http://atenatop.shop`
4. `http://maxatena.online`

A seleção ocorre em paralelo para reduzir o tempo de login e permitir fallback automático quando um servidor estiver indisponível.

### Melhorias mantidas
- Categorias navegáveis em TV ao Vivo, Filmes e Séries.
- Grade de conteúdo e filtro por nome dentro de cada catálogo.
- Busca global em canais, filmes e séries.
- Favoritos salvos localmente no aparelho.
- EPG/programação para canais compatíveis com `get_short_epg`.
- Múltiplas listas por dispositivo, com seleção dentro do app.
- Editor completo de ativações no painel.
- Logs/auditoria das principais alterações administrativas.
- Adaptadores esportivos para JSON genérico Akira, API-Football/API-Sports e football-data.org.
- Verificação automática de nova versão do APK.

## Aplicativo Android / Android TV

- Tela inicial Akira preparada para controle remoto.
- TV ao Vivo via API compatível com Xtream Codes.
- Filmes.
- Séries com temporadas e episódios.
- Player AndroidX Media3/ExoPlayer.
- Categorias por tipo de conteúdo.
- Busca dentro do catálogo.
- Busca global.
- Favoritos.
- EPG.
- Placar/status das partidas.
- Busca de canais relacionados ao jogo usando palavras-chave.
- Identificador do dispositivo.
- Registro do dispositivo no painel.
- Atualização remota de ativação.
- Múltiplas listas selecionáveis.
- Logo e fundo opcionais configuráveis pelo painel.
- Modo direto para teste sem painel.
- Atualizador de APK via painel.

## Painel PHP + SQLite

- Instalação inicial e login administrativo.
- Dashboard.
- Cadastro de servidores DNS.
- Ativações por dispositivo.
- Criação, edição, pausa, exclusão e definição da lista principal.
- Múltiplas listas por dispositivo.
- Dispositivos reconhecidos.
- Integração esportiva por cron.
- Identidade visual e suporte.
- Página pública de ativação.
- API JSON.
- Credenciais criptografadas com AES-256-GCM.
- Logs e auditoria.
- Controle de versão do APK.

## Testar agora sem painel

1. Abra a pasta `android` no Android Studio.
2. Faça o Gradle Sync.
3. Compile e instale o APK.
4. Ao abrir o app, a tela inicial será **Acessar Akira TV**.
5. Informe somente o **usuário** e a **senha** da lista.
6. Toque em **ENTRAR**.
7. O app testa automaticamente os 4 servidores embutidos.
8. Quando um deles autenticar, a tela principal é aberta.

Não é necessário preencher DNS para esse modo.

## Instalar o painel na Hostinger

1. Envie o conteúdo de `panel/` para `public_html/akira/`.
2. Use PHP 8.2 ou superior.
3. Confirme `PDO_SQLite`, `OpenSSL` e `JSON`.
4. Garanta permissão de escrita para `panel/data/`.
5. Abra `https://SEU-DOMINIO/akira/install.php`.
6. Crie o administrador.
7. Cadastre os servidores.
8. Cadastre as ativações.
9. No app, informe `https://SEU-DOMINIO/akira` no campo do painel.

O banco é criado em `panel/data/akira.sqlite` e a chave em `panel/data/app.secret`.

## Múltiplas listas

O endpoint `/api/config.php` agora retorna todas as ativações válidas do dispositivo em `activations` e também a lista principal em `activation`.

No app, abra **Minhas Listas** para alternar entre elas.

## EPG

Para canais compatíveis, o app chama:

`player_api.php?action=get_short_epg&stream_id=ID&limit=8`

No catálogo de TV, segure o botão OK em um canal e escolha **Ver programação (EPG)**.

## Jogos do Dia (painel legado — não usado pelo aplicativo)

### JSON genérico Akira

Formato esperado:

```json
{
  "games": [
    {
      "id": "evento-123",
      "starts_at": "2026-10-11 19:30:00",
      "competition": "Brasileirão Série A",
      "home_team": "Time A",
      "away_team": "Time B",
      "home_score": null,
      "away_score": null,
      "status": "scheduled",
      "channel_keywords": "Premiere,SporTV",
      "stream_id": ""
    }
  ]
}
```

### API-Football / API-Sports

Em **Configurações**:
- Provedor: `API-Football / API-Sports`
- URL: endpoint de fixtures do seu plano.
- Chave: sua chave do provedor.

O sincronizador envia a chave em `x-apisports-key`.

### football-data.org

Em **Configurações**:
- Provedor: `football-data.org`
- URL: endpoint de partidas.
- Chave: token do provedor.

O sincronizador envia a chave em `X-Auth-Token`.

### Cron

Exemplo:

```bash
*/10 * * * * /usr/bin/php /home/USUARIO/domains/SEU-DOMINIO/public_html/akira/cron/sync_games.php >/dev/null 2>&1
```

## Atualização do APK

No painel, em **Configurações**, informe:
- URL do APK;
- versão exibida;
- `version code`;
- mensagem da atualização.

O APK atual está com:
- `versionName = 1.2.0`
- `versionCode = 3`

Se o painel informar um `version code` maior que 3, o app oferece a atualização automaticamente.

## Logs

Abra **Logs / Auditoria** no menu lateral do painel para acompanhar ações administrativas como:
- login;
- criação/edição/exclusão de ativações;
- alteração da lista principal;
- servidores;
- jogos;
- configurações.

## Observação importante

O projeto não fornece canais, filmes, séries, credenciais nem fontes de transmissão. Configure apenas serviços e listas que você tenha autorização para usar.


## Correção de TV ao Vivo (v1.2.1)
O player ao vivo foi tornado mais tolerante a diferentes configurações Xtream. Para cada canal, o aplicativo pode usar o `direct_source` retornado pela API e também tenta automaticamente HLS (`.m3u8`), MPEG-TS (`.ts`) e a rota sem extensão. O player também permite redirecionamentos entre HTTP e HTTPS e usa as informações de host/porta e formatos permitidos retornadas no login, quando disponíveis.

Após instalar a v1.2.1, é recomendável usar **Configurações > Trocar usuário / Sair** e entrar novamente uma vez, para que o aplicativo salve as novas informações de reprodução fornecidas pelo servidor.

### Interface de catálogo 1.4
As telas de TV ao Vivo, Filmes e Séries usam navegação em duas colunas: categorias à esquerda e conteúdo à direita. Cada categoria mostra a quantidade de itens. Em TVs, use ↑/↓ nas categorias, → para entrar nos conteúdos e ← para voltar às categorias.

### Histórico e retomada (v1.5.0)
O aplicativo mantém localmente um histórico de conteúdos assistidos por usuário. TV ao Vivo, Filmes e Séries possuem a categoria **Assistidos recentemente**. Filmes e Séries também possuem **Continuar assistindo**. Filmes e episódios interrompidos são retomados automaticamente do ponto salvo. Para séries, o app registra o último episódio assistido e destaca esse episódio ao reabrir a série; quando o episódio anterior foi concluído, o próximo episódio é priorizado quando disponível.
