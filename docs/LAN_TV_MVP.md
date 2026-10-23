# FREZZAMUSIC TV — conexão direta por Wi-Fi (MVP experimental)

Esta integração **não usa Google Cast**. O celular publica temporariamente uma página HTTP na LAN; o navegador da TV acessa o endereço exibido no celular, e o usuário informa o PIN.

## Como experimentar (após build validada)
1. Celular Android e TV na mesma rede Wi-Fi, sem isolamento entre clientes.
2. No player, escolher **Conectar TV pelo Wi-Fi (sem Cast)**.
3. Na TV, abrir o navegador e digitar o endereço `http://IP_DO_CELULAR:PORTA` exibido pelo app.
4. Digitar o PIN de seis dígitos mostrado no celular.
5. O navegador atualiza título, artista, álbum e linha LRC a cada segundo.

## Limitações importantes
- **O áudio ainda toca no celular.** Envio de áudio, fila/áudio visual, descoberta via mDNS e pareamento por QR serão etapas seguintes.
- PIN é uma proteção simples de acesso à informação de reprodução, **não criptografa** o transporte HTTP. Usar apenas em redes confiáveis; não expor portas à Internet.
- O servidor encerra ao sair do player. Não depende de nuvem ou contas Google.
- O endereço obtido via Wi-Fi pode não funcionar com VPN, AP isolation, hotspot restritivo ou em algumas redes/televisores.
- O receptor web exibido é uma versão técnica inicial e ainda não equivale à interface definitiva das quatro abas.
- **Não substituir APKs do Drive** sem compilação bem-sucedida, revisão de interface e teste da funcionalidade anterior.
