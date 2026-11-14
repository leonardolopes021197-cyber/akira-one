# Notas de segurança

Esta versão continua sendo uma base funcional/MVP, mas agora inclui auditoria e atualização remota do APK.

- O painel usa sessão PHP, `password_hash`, prepared statements e CSRF.
- Usuários e senhas das listas são criptografados com AES-256-GCM.
- A chave fica em `panel/data/app.secret`.
- Dispositivos usam token próprio para consultar a configuração.
- O painel possui logs administrativos.
- Prefira HTTPS no painel e nos servidores de conteúdo.
- O modo direto usa `http://akiratv.online` por solicitação para facilitar testes; para produção, HTTPS é preferível.
- O aplicativo mantém `usesCleartextTraffic=true` para aceitar esse DNS HTTP.
- Atualizações de APK devem ser hospedadas em URL confiável, preferencialmente HTTPS.
- O Android ainda exige confirmação do usuário para instalar um APK atualizado.

Para uma operação comercial maior, ainda é recomendável adicionar rate limiting, expiração/rotação de tokens de dispositivo, proteção contra tentativas repetidas de login e revisão de segurança independente.
