# AKIRA ONE 1.12.1 — Corrige tremor na Início e foco pulando para o menu

- Corrigido: a tela de Início tremia na TV. A área principal ficava dentro de um ScrollView com alturas por peso e lista de pôsteres, e as medidas oscilavam sem parar. Agora a Início tem altura fixa da tela e banner e lançamentos dividem o espaço por peso.
- Corrigido: ao descer rápido com o controle nas categorias (e nos canais), o foco pulava para o menu lateral (Filmes/Séries) em vez de continuar descendo. As listas agora movem o foco item a item e só deixam o foco sair pelo início. Mesma proteção na grade de filmes/séries (para baixo/cima) e na faixa de Lançamentos (esquerda/direita).

# AKIRA ONE 1.12.0 — Atualização automática

- Atualização automática: ao abrir o app (login ou home), ele consulta o GitHub; se houver versão maior, mostra o aviso com o que mudou, baixa o APK com barra de progresso e abre o instalador do Android. Também há o botão Configurações → Sobre → Verificar atualização.
- O GitHub Actions agora publica cada versão no repositório público de releases (AKIRA_ONE.apk + version.json), usando o segredo RELEASES_TOKEN. Sem o segredo, o passo é ignorado e nada quebra. Instruções em ATUALIZACAO_AUTOMATICA.md.
- Chave de assinatura fixa (android/akira-signing.jks) para todas as versões. Sem ela o Android recusa atualizar por cima. ATENÇÃO: quem tem o app instalado de uma versão anterior precisa desinstalar uma vez antes de instalar a 1.12.0; depois disso as atualizações entram por cima sozinhas.

# AKIRA ONE 1.11.0 — Canais em 3 colunas, programa em cada canal e sem agrupar qualidades

- TV ao Vivo em três colunas: categorias (com busca), canais (com busca) e EPG do canal selecionado. A coluna da direita mostra logo, nome, coração de favorito, a lista de programas (o que está no ar fica destacado com barra de progresso e tempo restante) e os botões ASSISTIR e EPG.
- Embaixo do nome de cada canal aparece, em fonte menor, o programa que está passando agora. A busca é feita só para os canais visíveis, no máximo 3 por vez, com cache de 5 minutos e descarte de pedidos de canais que já saíram da tela, para não sobrecarregar o servidor.
- Removido o agrupamento de qualidades: os canais aparecem exatamente como vêm da lista (SporTV FHD, HD e SD separados). Sem agrupamento, o botão HD do player não aparece mais e OK abre o canal direto.

# AKIRA ONE 1.10.0 — TV ao Vivo moderna e home só com o menu

- TV ao Vivo refeita: categorias viraram pílulas horizontais (a selecionada em degradê azul/roxo), canais em grade de cartões com logo grande e número, e novo painel de destaque à direita com logo, selo AO VIVO, programa atual, barra de progresso, tempo restante, próximos programas e botões ASSISTIR, favoritar (coração) e EPG. O painel não usa vídeo, então não gasta uma segunda conexão da lista.
- Controle remoto: nas pílulas, "baixo" vai para os canais; na grade, as setas andam entre os cartões e só saem pelas pontas (esquerda → menu lateral, direita → botão ASSISTIR).
- Home: removidos os três cards do meio (TV ao Vivo, Filmes, Séries) e os botões redondos do topo. Tudo se acessa pelo menu da esquerda, que ganhou o item Pesquisar. O banner ficou maior.

# AKIRA ONE 1.9.0 — Novo login e nova tela de canais

- Login redesenhado: fundo azul escuro com brilho, logo no topo, campos largos cinza, botões ENTRAR e MOSTRAR SENHA em degradê roxo/azul e ID do aparelho no canto inferior esquerdo. Mantidos o aviso vermelho de usuário/senha inválidos e o preenchimento da última conta.
- TV ao Vivo redesenhada: menu lateral de ícones (pesquisar, início, TV, filmes, séries, favoritos, configurações e sair), título com data e hora, listas planas com busca em contorno, categoria selecionada com barra azul e ícone azul de "ao vivo" no canal selecionado.
- Novo painel de informações do canal (sem vídeo, para não gastar uma segunda conexão da lista): logo, nome, coração de favorito, programação atual em amarelo e próximos programas, botões EPG e Adicionar aos favoritos. OK continua abrindo o canal em tela cheia.
- Cabeçalho, painéis e listas das telas de Filmes e Séries também ganharam o novo estilo (categorias planas, busca em contorno).

# AKIRA ONE 1.8.3 — Corrige o player que fechava ao abrir

- Corrigido: ao abrir um canal, filme ou episódio o app fechava (erro "Error inflating class" em activity_player). Os botões de voltar/avançar 10 s da barra nova eram de um tipo que o Media3 não aceita; agora usam o formato oficial da biblioteca, e o número (10) é preenchido por ela.
- Troca de conta: o catálogo, os downloads em andamento e os arquivos da conta anterior agora são limpos em qualquer caminho que mude o usuário (login, Sair, troca de lista/perfil), não só no login.

# AKIRA ONE 1.8.2 — Troca de usuário e aviso de login

- Corrigido: ao trocar de usuário, o conteúdo do usuário anterior podia aparecer na conta nova. Causa: uma lista ainda baixando para o usuário antigo era gravada como se fosse do novo. Agora cada download fica preso ao usuário que o pediu, e ao sair/trocar de conta o catálogo (memória, downloads em andamento e arquivos) é limpo.
- Corrigido: favoritos eram compartilhados entre todos os usuários. Agora cada conta tem os seus (os favoritos antigos ficam com a conta que estava ativa).
- Login com usuário ou senha que não funcionam: aparece um aviso vermelho "Usuário ou senha não está funcionando", os campos ficam com contorno vermelho e o aviso some ao voltar a digitar. Sem internet, o aviso informa que não foi possível conectar.

# AKIRA ONE 1.8.1 — Compilação automática no GitHub

- Novo arquivo `.github/workflows/build-apk.yml`: ao enviar o projeto para o GitHub, o APK é compilado sozinho (Java 17, Gradle 8.9) e publicado em Releases como `AKIRA_ONE_v<versão>.apk`.
- Nenhuma mudança no código do app.

# AKIRA ONE 1.8.0 — Layout moderno

- Home reconstruída em layout nativo: menu lateral com ícones, banner, atalhos TV/Filmes/Séries e relógio. Antes era uma imagem fixa com botões invisíveis por cima.
- Nova faixa "Lançamentos" com os 16 filmes mais recentes do catálogo do usuário (capas reais, clique abre o filme). Os pôsteres de exemplo da arte antiga foram removidos.
- Home adapta a celular, TV 720p/1080p e 4K, respeita notch/câmera e rola em telas baixas.
- Novo sistema visual: fundo com brilho suave, superfícies arredondadas, foco branco com leve zoom para controle remoto, tipografia mais limpa e ícones em linha.
- Login, Configurações, Busca, Catálogo (TV/Filmes/Séries), listas de episódios e itens de lista redesenhados.
- Pôsteres do catálogo com cantos arredondados; categorias com contador em chip e destaque de seleção.
- Arte original da home movida para `design/akira_home_reference.png` (fora do APK); o banner usa `akira_hero.jpg` recortado dela. APK menor (~2,7 MB a menos).
- Player redesenhado: o nome do canal não fica mais fixo no topo (o topo só mostra avisos temporários como "Conectando…"). Nova barra de controles Akira com degradê, nome e selo AO VIVO, tempo e progresso vermelho, voltar/avançar 10 s, play/pause em destaque e botão HD integrado. Os controles somem sozinhos após 4 s.
- Diálogos do app (qualidade HD, favoritos, atualização) com fundo escuro arredondado e destaque vermelho.
- O app agora sempre abre na tela de usuário e senha (com os dados da última sessão já preenchidos). Em modo painel, que não usa login, continua indo direto à home.
- Nova tela de carregamento após o login: barra de progresso real da atualização de TV ao vivo, Filmes e Séries, com botão "Continuar sem esperar" após 10 s e limite de 60 s (o download termina em segundo plano).
- Nenhuma mudança nos servidores, no EPG, nos favoritos nem no painel. Todos os IDs de views usados pelo código foram mantidos.

# AKIRA ONE 1.7.10

- Botão HD agora acompanha a visibilidade dos controles do player e some junto com a engrenagem/barra de controles.
- Mantida troca manual de qualidade e fallback automático da 1.7.9.
- Tela inicial mostra a data de vencimento da lista no formato dd/MM/yyyy.
- Vencimento é lido do `exp_date` do Xtream e atualizado silenciosamente ao entrar na home.

# AKIRA ONE 1.7.6

- Removido o sistema de preview dos canais ao vivo.
- TV ao Vivo voltou ao layout de duas colunas: categorias + canais.
- Um toque/OK no canal abre diretamente em tela cheia.
- Seleção de qualidade continua disponível dentro do player em tela cheia pelo botão OK/Enter.
- Mantido agrupamento de SD/HD/FHD/4K, com HD como padrão quando disponível.

# AKIRA ONE 1.7.1

- Pré-carregamento automático de TV ao Vivo, Filmes e Séries após o login.
- Cache em memória durante a sessão para abrir os catálogos sem nova espera.
- Cache persistente no armazenamento interno para acelerar as próximas aberturas.
- Atualização automática do catálogo em segundo plano a cada 30 minutos.
- Se a atualização falhar, o aplicativo continua usando o último catálogo salvo.

# 1.5.1
- Removida completamente a área **Jogos do Dia** do aplicativo Android.
- Removidos atalho por toque longo, activity, adapters, modelos e layouts de jogos do APK.
- Histórico, Continuar Assistindo, TV ao Vivo, Filmes e Séries permanecem inalterados.
- O módulo de jogos do painel PHP foi mantido apenas no painel e não é exposto pelo aplicativo.

# Changelog

## 1.3.0
- Nova tela inicial AKIRA em 16:9 baseada exatamente na referência visual enviada.
- Menu lateral interativo: Início, TV ao Vivo, Filmes, Séries, Favoritos e Configurações.
- Cards centrais TV ao Vivo, Filmes e Séries totalmente clicáveis.
- Ícones de busca/configurações e área de lançamentos interativos.
- Navegação preparada para controle remoto com destaque de foco.
- Tela inicial em modo imersivo e orientação paisagem.
- Jogos do Dia preservado via toque longo em Início.
- Mantidos login automático pelos 4 DNS e correções do player 1.2.1.

## 1.2.1
- Correção da reprodução dos canais ao vivo.
- Player agora tenta automaticamente `direct_source`, HLS (`.m3u8`), MPEG-TS (`.ts`) e rota sem extensão.
- Suporte a redirecionamento HTTP ↔ HTTPS no player.
- User-Agent compatível com servidores que bloqueiam o identificador padrão do ExoPlayer.
- Leitura do host/porta de reprodução retornado pelo `player_api.php`.
- Leitura de `allowed_output_formats` do servidor.
- Adicionado módulo Media3 HLS.
- Mensagens de conexão/fallback e erro mais claras no player.
- `versionCode` atualizado para 4.

## 1.2.0
- Nova tela inicial de login com usuário e senha.
- Quatro servidores/DNS Akira embutidos no APK.
- Seleção automática e paralela do primeiro servidor que autenticar.
- DNS ocultos da interface do cliente.
- Botão Trocar usuário / Sair nas Configurações.
- Migração da versão anterior: servidor antigo não embutido força novo login.
- `versionCode` atualizado para 3.

## 1.1.0
- DNS direto padrão `http://akiratv.online`.
- Modo de teste sem painel.
- Campos de usuário/senha/lista no próprio app.
- Categorias e filtro de catálogo.
- Busca global.
- Favoritos.
- EPG.
- Múltiplas listas selecionáveis.
- Editor de ativações.
- Logs/auditoria.
- Adaptadores API-Football e football-data.org.
- Atualização de APK por version code.

## 1.4.0 — Catálogo em duas colunas
- TV ao Vivo, Filmes e Séries agora abrem com categorias na coluna esquerda e itens da categoria na coluna direita.
- Contador de itens por categoria.
- Ao navegar pelas categorias com o controle remoto, a lista da direita atualiza automaticamente.
- DPAD Direita entra na lista de conteúdo; DPAD Esquerda retorna para a categoria selecionada.
- Busca filtra somente a categoria atual.
- Layout otimizado para TV em 16:9 e orientação horizontal.

## 1.5.0
- Adiciona "Assistidos recentemente" em TV ao Vivo, Filmes e Séries.
- Adiciona "Continuar assistindo" para Filmes e Séries.
- Filmes retomam automaticamente do ponto em que foram interrompidos.
- Episódios retomam automaticamente do ponto salvo.
- Registra o último episódio assistido de cada série.
- Ao reabrir uma série, destaca o último episódio; se ele foi concluído, direciona para o próximo quando disponível.
- Histórico e progresso ficam separados por usuário.
- Progresso é salvo periodicamente e também ao sair do player.

## 1.6.0
- Séries organizadas por temporada, com temporadas na coluna esquerda e episódios na direita.
- Controle parental para categorias de canais adultos; PIN padrão 0000 e alteração em Configurações.
- Conteúdo adulto oculto da categoria "Todos" e de "Assistidos recentemente" enquanto bloqueado.
- Novo visual das listas de TV, Filmes e Séries inspirado em layout de duas colunas escuro/ciano.
- Filmes e Séries continuam usando grade de capas no painel de conteúdo.
- Mantidos histórico, continuar assistindo e progresso do último episódio.

## 1.6.1 - Layout responsivo
- Home preserva a proporção 16:9 sem esticar a arte em telas 18:9, 19.5:9 ou 20:9.
- Hotspots da home agora acompanham a área real renderizada da imagem.
- Catálogos usam colunas proporcionais em vez de larguras fixas.
- Grade de capas calcula automaticamente a quantidade de colunas conforme largura e densidade da tela.
- Capas preservam proporção vertical e se redimensionam dinamicamente.
- Tela de temporadas usa proporções responsivas.
- Login e campos do controle parental deixaram de depender de larguras fixas.
- Telas principais trabalham em sensorLandscape para manter orientação horizontal consistente.

## 1.7.0
- TV ao Vivo redesenhada em três áreas: categorias, canais e preview.
- Preview automático do canal ao focar na lista, com atraso curto para evitar trocas excessivas.
- Tela cheia somente após confirmar novamente o canal já carregado no preview.
- EPG e Favoritos disponíveis no painel do preview.
- Busca independente de categorias e canais na tela de TV ao Vivo.

## 1.7.2
- Tela cheia real em celulares 16:9, 18:9, 19.5:9, 20:9 e 21:9.
- Home usa centerCrop responsivo para eliminar letterbox/bordas pretas sem distorcer a arte.
- Hotspots da home acompanham automaticamente o recorte em qualquer proporção.
- Todas as telas ocupam as barras/recorte do display em modo imersivo.
- Todas as Activities abrem forçadamente em paisagem, mesmo com o celular segurado em pé ou rotação automática desativada.

## 1.7.3
- Agrupamento de canais ao vivo por nome: SD/HD/FHD/4K deixam de aparecer como canais duplicados.
- O nome exibido remove o sufixo de qualidade (ex.: "ESPN 1 HD" passa a "ESPN 1").
- Qualidade HD é escolhida por padrão quando disponível; fallback para FHD, 4K, SD ou original.
- Preview de TV ao vivo ganhou seletor de qualidade.
- No player em tela cheia, pressionar OK/Enter abre o seletor de qualidade do canal.
- Troca de qualidade refaz a reprodução sem sair do canal.
