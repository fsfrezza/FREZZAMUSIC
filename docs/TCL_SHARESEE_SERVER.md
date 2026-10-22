# FREZZAMUSIC TV — TCL L43S4900FS (Share&See)

## Modos suportados no aplicativo

1. **Servidor Share&See (DLNA MediaServer / DMS):** o celular anuncia um servidor UPnP para a TCL navegar. Na TV, com Share&See ligado, abra o navegador de mídia do aparelho e procure `FREZZAMUSIC - Biblioteca`. A lista expõe até 300 itens da fila carregada no player (não todas as faixas do armazenamento); é preciso selecionar uma faixa para formar a fila antes de ligar o servidor. Reprodução é comandada pela TV. Funciona só enquanto o player está aberto e a tela mantém o servidor ativo.
2. **TVs DLNA MediaRenderer (DMR):** use `Procurar TVs na rede Wi-Fi`. Somente televisores que se anunciam como DMR podem aparecer e aceitar os comandos enviados pelo telefone.
3. **Google Cast:** permanece alternativa para receptores compatíveis, sem garantir Cast na TCL L43S4900FS.
4. **Miracast/espelhamento:** opção abre as configurações do Android para espelhamento quando disponíveis. Isso **não** implementa o protocolo Miracast nem habilita função ausente na televisão.

## Limitações do primeiro protótipo DMS

- O protocolo SSDP e ContentDirectory são uma implementação experimental, sem teste físico na TCL; TV pode exigir outros comandos UPnP e perfis DLNA.
- O anúncio e a reprodução usam HTTP na rede local, sem autenticação, portanto somente redes confiáveis. Desative o servidor fora de casa.
- O fluxo serve URIs do player (locais ou online) e anuncia `audio/mpeg` de forma conservadora; outros codecs, licenças, credenciais e respostas HTTP parciais podem não ser compatíveis.
- Letras sincronizadas e controle da tela da TV pelo celular **não** estão disponíveis pelo DMS. O TCL Share&See é utilizado para descoberta e reprodução de músicas na própria TV.
- Para as telas Tocar/Letras independentes é necessário um receptor personalizado compatível com o sistema da TV, uma página web aberta pelo navegador ou um dispositivo externo apropriado.
- A descoberta SSDP exige que roteador não isole clientes Wi-Fi e libere multicast 239.255.255.250:1900.
- Compilação aprovada não equivale a compatibilidade confirmada. Testar em aparelho real antes de promover versão.

## Validação manual

1. Na TCL L43S4900FS ligar Share&See, TV e celular na mesma rede.
2. No app escolher uma música para carregar a fila, abrir o player e ligar `Compartilhar músicas com Share&See`.
3. Abrir o navegador de mídia da TCL e localizar o servidor FREZZAMUSIC. Listar títulos e reproduzir MP3.
4. Confirmar troca de faixa, término da faixa, desligamento do servidor e operação dos três aplicativos.
5. Confirmar que ícones, botões de atualização e reprodução local permanecem funcionais.
