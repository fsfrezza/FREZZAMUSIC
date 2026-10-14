# Ordenação de álbuns por sequência de pastas

Na ordenação Data, uma série validada de álbuns usa sua numeração em vez de inferir datas fictícias. A detecção é feita antes dos filtros de busca, por pasta pai. É necessário haver ao menos três pastas de álbuns, todas numeradas de 1 até N sem lacunas ou duplicatas, com separador igual (hífen ou ponto seguido de espaço) e preenchimento de zeros consistente. Números de quatro dígitos (anos), números isolados e coleções misturadas não são aceitos.

No Drive, somente pastas que contêm faixas participam. No armazenamento local SAF, são examinadas as subpastas irmãs e suas faixas, incluindo subpastas internas; cada pasta deve representar um álbum distinto. MediaStore usa os caminhos relativos completos quando disponíveis (Android 10+); não infere uma sequência com apenas o nome final da pasta.

Nomes, datas reais e IDs não são alterados. Entre coleções independentes, a posição de cada coleção segue a ordenação normal por data; somente os espaços ocupados por álbuns da mesma sequência são reordenados. Crescente e decrescente continuam disponíveis. A numeração remota é persistida no cache e atualizada pelo fluxo normal da biblioteca.
