# Atualização do catálogo online

Os três aplicativos consultam as pastas públicas MP3 já utilizadas pelo catálogo. Cada subpasta representa um álbum; arquivos MP3 são ordenados pelo prefixo numérico. Imagens PNG/JPG/JPEG/WebP fornecem a capa, com preferência por nomes contendo capa, cover ou front. Novos álbuns recebem IDs derivados da pasta; os álbuns existentes preservam seus IDs para manter favoritos e listas.

Ao abrir o app, o catálogo salvo é apresentado e uma consulta online é iniciada. Em Mais, Atualizar biblioteca atualiza os arquivos locais e o catálogo online. A aba Externas também oferece atualização. Mudanças de reprodução não disparam consultas de rede. A atualização é atômica por artista: uma falha em qualquer pasta mantém o catálogo anterior daquele artista. Remoções e renomeações são refletidas após uma consulta bem-sucedida.

Solasias consulta apenas sua pasta; theFrezza consulta apenas sua pasta; FREZZAMUSIC consulta ambas. O cache guarda somente metadados, não os MP3. Não há credenciais pessoais dentro do APK e nenhuma permissão de compartilhamento é alterada.

Limitação: a descoberta usa a visualização pública embeddedfolderview do Drive, que não é uma API estável documentada. Depende de acesso público e da estrutura HTML do Google. Páginas inválidas, erros de acesso e pastas com 100 ou mais itens são rejeitados para evitar aceitar uma listagem potencialmente incompleta. Uma mudança de formato do Google exige adaptação; o catálogo anterior permanece disponível com aviso. Para escala e operação de produção, migrar para um serviço de catálogo com Drive API autenticada no servidor e paginação documentada.

Os testes usam capturas das duas raízes e do álbum Kaleidophrenia, validando descoberta, ordenação, capa, preservação dos IDs e rejeição de respostas inválidas/incompletas. Instalação, streaming e retorno de conexão ainda devem ser testados em aparelho.
