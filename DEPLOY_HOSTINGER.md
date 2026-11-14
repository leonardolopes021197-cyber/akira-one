# Deploy rápido — Hostinger

## Painel
1. Gerenciador de Arquivos > `public_html`.
2. Crie a pasta `akira`.
3. Envie todo o conteúdo de `panel/` para `public_html/akira/`.
4. Confirme que `public_html/akira/data/` é gravável pelo PHP.
5. Acesse `https://seu-dominio.com/akira/install.php`.
6. Crie o administrador.
7. Cadastre o DNS/servidor e depois uma ou mais ativações.

## PHP
Use PHP 8.2 ou superior com:
- PDO
- pdo_sqlite
- openssl
- json

## Segurança
- Use HTTPS no painel.
- Não deixe backup público da pasta `data`.
- Use senha forte.
- O `.htaccess` bloqueia os arquivos sensíveis quando Apache/LiteSpeed respeita as regras.

## App — modo direto
A versão 1.1 já vem com:

`http://akiratv.online`

como DNS padrão de teste. Deixe o campo do painel vazio e informe usuário/senha em Configurações.

## App — modo painel
No app, configure a URL **sem `/api`**:

`https://seu-dominio.com/akira`

## Atualização do APK
Hospede o APK em uma URL HTTPS e informe essa URL em **Configurações > Atualização do APK**. Aumente o `version code` sempre que publicar uma versão nova.
