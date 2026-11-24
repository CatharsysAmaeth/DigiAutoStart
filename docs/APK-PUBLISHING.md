# Publicação de APKs

O workflow de Android compila e testa as variantes Debug e Release.

- O APK estável é publicado como ficheiro anexo a uma GitHub Release, depois de as alterações serem integradas na branch `main`.
- Cada versão usa uma etiqueta própria, no formato `vX.Y.Z`, e o nome do APK inclui a versão.
- Os APKs binários não são guardados na árvore de código do repositório público.
- A versão Release inclui o ecrã de configuração e a automação de standby/arranque testada.
- A versão Release exclui menus de desenvolvimento, recolha de diagnósticos, ferramentas privadas de teste e permissões de rede/instalação exclusivas de Debug.
- As compilações Debug destinam-se ao desenvolvimento e a testes no equipamento; não são publicadas como versões estáveis.
- Os pedidos de integração compilam e testam ambas as variantes, mas não publicam uma versão.

O workflow verifica o manifesto e os ficheiros DEX da Release: a automação tem de estar presente e os diagnósticos privados e ferramentas de Debug têm de estar ausentes.

O material de assinatura e as credenciais são mantidos fora do código público, em configuração protegida do CI. Nunca incluir num commit um keystore, chave privada, palavra-passe, token ou credencial.
