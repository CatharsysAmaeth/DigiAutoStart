# DAdA — DIGI TV · Assistente de arranque

O DAdA é uma aplicação leve para Android TV que permite voltar a abrir a aplicação de televisão selecionada depois de a box sair do modo de espera e, opcionalmente, quando a box é ligada.

## Versão atual: 0.6.0

- **Modo A:** volta a abrir a aplicação de televisão selecionada sempre que a box desperta.
- **Modo B:** só volta a abrir a aplicação se esta era a aplicação em primeiro plano antes de a box entrar em modo de espera.
- Atraso configurável antes de voltar a abrir a aplicação.
- Opção independente para abrir a aplicação quando a box arranca.
- Recuperação automática da monitorização depois de reiniciar a box ou atualizar a aplicação.
- Ecrã único **«Configurar»** para escolher o modo, a aplicação de destino e as permissões necessárias.
- A aplicação oficial da DIGI TV é o destino predefinido. Se não estiver disponível, podem ser detetadas aplicações compatíveis; quando existem várias possibilidades, a seleção é feita manualmente.
- A versão estável inclui a automação e a configuração, mas não inclui menus de desenvolvimento, recolha de diagnósticos ou ferramentas de atualização de teste.

## Compatibilidade e segurança

- Desenvolvido para dispositivos Android TV com Android 12 ou posterior.
- Mantém o launcher original e a certificação da plataforma.
- Não requer acesso root, desbloqueio do bootloader, firmware personalizado ou alterações a partições protegidas.

As políticas do Android e do fabricante relativas à execução em segundo plano podem limitar a abertura automática. O DAdA verifica se a aplicação selecionada passa efetivamente para primeiro plano; o simples pedido de abertura não garante que fique visível.

## Transferir

A versão estável mais recente está disponível nas [GitHub Releases](https://github.com/CatharsysAmaeth/DigiAutoStart/releases/latest).

As versões anteriores e as respetivas notas estão disponíveis na página de [todas as versões](https://github.com/CatharsysAmaeth/DigiAutoStart/releases).

Consulta o [histórico de alterações](CHANGELOG.md) para conhecer a evolução do DAdA.
