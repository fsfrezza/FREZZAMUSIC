# Ordenação de álbuns por sequência de pastas

Na ordenação Data, uma série validada de álbuns usa sua numeração em vez de inferir datas fictícias. A detecção é feita antes dos filtros de busca, por pasta pai. É necessário haver ao menos três pastas de álbuns, todas numeradas de 1 até N sem lacunas ou duplicatas, com separador igual (hífen ou ponto seguido de espaço) e preenchimento de zeros consistente. Números de quatro dígitos (anos), números isolados e coleções misturadas não são aceitos.

No Drive, somente pastas que contêm faixas participam. No armazenamento local SAF, são examinadas as subpastas irmãs e suas faixas, incluindo subpastas internas; cada pasta deve representar um álbum distinto. MediaStore usa os caminhos relativos completos quando disponíveis (Android 10+); não infere uma sequência com apenas o nome final da pasta.

Nomes, datas reais e IDs não são alterados. Entre coleções independentes, a posição de cada coleção segue a ordenação normal por data; somente os espaços ocupados por álbuns da mesma sequência são reordenados. Crescente e decrescente continuam disponíveis. A numeração remota é persistida no cache e atualizada pelo fluxo normal da biblioteca.

## Prioridade da data real

Uma data válida com mês ou dia, compartilhada por mais da metade das faixas, prevalece sobre a numeração. Datas divergentes sem maioria não são inventadas; conserva-se somente um ano se houver maioria para esse ano. A numeração só reordena álbuns sem data detalhada e dentro do mesmo ano conhecido (ou da coleção sem ano). Datas de modificação são mantidas separadas e usadas apenas no comportamento de fallback local.

Faixas locais: leitura das tags ID3 e das datas do MediaMetadataRetriever. Drive: leitura limitada aos primeiros 64 KiB dos MP3, com até oito requisições simultâneas, sem salvar áudio. Datas consultadas ficam em cache por 24 horas; atualização manual refaz a consulta. Tags ausentes, inválidas, comprimidas ou fora do limite não geram datas fictícias.
