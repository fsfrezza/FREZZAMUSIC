# Transmissão por Wi-Fi: FREZZAMUSIC / SOLASIAS / theFREZZA

## Primeira implementação (branch feature/wifi-google-cast)

- O player compartilhado inclui botão Google Cast, usando o SDK oficial e o receptor padrão do Google.
- O SDK descobre receptores Google Cast disponíveis na mesma rede, inclusive TVs com Cast integrado; não é necessário hardware Chromecast externo se a TV já dispõe de receiver.
- Quando há sessão Cast conectada, o usuário pode enviar a faixa atual, desde que sua URL HTTP(S) seja acessível pela TV. Faixas remotas passam pelo resolver existente. O áudio local com URI `content://` não pode ser tocado diretamente na TV e exige um servidor temporário seguro ou um receiver próprio.
- O receptor padrão exibe interface de mídia e metadados. **Ainda não há exibição de letras personalizadas nem DLNA/AOC universal.**
- Bluetooth não faz parte desta fase.
- Esta branch precisa de compilação Android e teste com aparelhos reais. Não foi declarado APK concluído.

## Compatibilidade e testes

Apenas a marca (TCL 43” e AOC) não identifica o sistema operacional nem a presença de Cast. Teste com ambas no mesmo Wi-Fi e confirme no menu da TV se há Google Cast, Chromecast built-in ou app receptor compatível.

Casos de teste:
1. Abrir o player, localizar TV e conectar;
2. Verificar se os metadados e a mídia remota HTTP(S) são acessíveis pelo receiver;
3. Pausar no celular após enviar ao receptor, sem áudio duplicado;
4. Exibir erro claro se mídia local não puder ser compartilhada;
5. Encerrar sessão e controlar novamente no celular;
6. Confirmar comportamento nas três variantes de sabor Android.

## Próximas fases
- Implementar receiver visual próprio com a linha de letra atual, capa do álbum e status do player; uso de .lrc exige converter para WebVTT acessível ou comunicação customizada entre sender e receiver;
- Streaming HTTP local autenticado e temporário para músicas locais;
- Descoberta e envio DLNA/UPnP para TVs sem Cast; suporte a conteúdo/legendas varia por marca e modelo;
- Progresso, erros e controles remotos de play/pause/seek/fila;
- Verificações de ciclo de vida e testes instrumentados na TV.

O aplicativo Android usa Media3; o receptor Google Cast mantém uma sessão remota distinta. É necessário coordenar estados, fila e volume para que a experiência funcione como um único player.
