# Rankings globais — v0.22.6

Os três aplicativos compartilham a API https://frezzamusic-global-rankings.fondfield256.chatgpt.site. A tela inicial apresenta até dez músicas mais ouvidas e mais curtidas. SOLASIAS e theFrezza mostram a respectiva banda; FREZZAMUSIC permite escolher Geral, SOLASIAS e theFrezza. SOLASIAS.apk e o nome do aplicativo usam maiúsculas.

Uma reprodução conta depois de 30 segundos efetivamente tocados; pausa e buffering não acumulam tempo. Cada passagem pela faixa gera um evento próprio e as tentativas de envio repetidas não duplicam contagens. Curtir/descurtir usa sequência monotônica; cada instalação anônima tem um voto por faixa. Quando um login Firebase válido está configurado, o mesmo usuário tem um voto ativo por faixa entre os aplicativos. Músicas locais não participam. O servidor valida a faixa no catálogo oficial do Drive.

A fila de eventos fica persistida no armazenamento privado e tenta envio novamente ao abrir o aplicativo e a cada 30 segundos enquanto o processo estiver ativo. Eventos de reprodução expiram após 24 horas. Os rankings são consultados a cada 30 segundos enquanto a tela inicial estiver visível e também pelo botão de atualização. Sem dados, as listas ficam vazias; nenhum histórico local é apresentado como contagem global. A coleta começa nesta versão.

O código do serviço e as migrações D1 estão em `backend/rankings-service`. Testes: `node --test test/*.test.mjs` nesse diretório; desenvolvimento/build: `pnpm install --frozen-lockfile`, `pnpm exec tsc --noEmit`, `pnpm build`. O serviço está publicado e acessível aos ouvintes dos três aplicativos. Tokens de instalação são aleatórios e seus hashes são armazenados no servidor; não há segredo administrativo no APK.

## Identidade para relatórios futuros

A integração usa o login Firebase já previsto em PurchaseIdentity. Só identidades verificadas por assinatura, projeto, emissor e validade são associadas aos eventos. O UID, nome e email retornados pelo provedor podem ser guardados no banco privado; senha e token de login não são persistidos. Dados pessoais não são retornados pelos rankings públicos. A identidade de cada reprodução é definida no início e não muda quando a conta troca.

A configuração Firebase dos APKs ainda está vazia. Para ativar o vínculo real, configurar as propriedades Firebase existentes no build Android e `FIREBASE_PROJECT_ID` no ambiente do serviço com o mesmo projeto. Não habilitar a API com um projeto arbitrário. Sem essa configuração, a coleta funciona por instalação anônima. A interface de relatórios pessoais não faz parte desta versão.

Validação física de áudio, desconexão do cabo e interface depende de aparelho Android; testes automatizados validam a contagem com pausas, reinícios e o serviço compartilhado.
