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
- As edições estáveis incluem a automação e a configuração, mas não incluem menus de desenvolvimento, recolha de diagnósticos ou ferramentas de atualização de teste.

## Edições e compatibilidade

O projeto publica duas edições independentes, com a mesma funcionalidade:

| Edição | Android mínimo | Identificador da aplicação |
|---|---:|---|
| **DAdA Android 12** | Android 12 (API 31) | `pt.catharsys.digiautostart` |
| **DAdA Android 9** | Android 9 (API 28) | `pt.catharsys.digiautostart.android9` |

As duas edições podem estar instaladas simultaneamente no mesmo equipamento Android 12 ou posterior. A edição Android 9 é uma compilação de compatibilidade, não uma atualização da instalação Android 12.

A edição Android 9 remove do Manifest a declaração de foreground service introduzida no Android 14; o serviço usa a chamada compatível com versões anteriores nos sistemas antigos. O Modo B usa os eventos de utilização disponíveis no sistema: no Android 9, o evento legado de passagem para primeiro plano.

## Segurança e limitações

- Mantém o launcher original e a certificação da plataforma.
- Não requer acesso root, desbloqueio do bootloader, firmware personalizado ou alterações a partições protegidas.
- As políticas do Android e do fabricante relativas à execução em segundo plano podem limitar a abertura automática. O DAdA verifica se a aplicação selecionada passa efetivamente para primeiro plano; o simples pedido de abertura não garante que fique visível.
- A compatibilidade de compilação Android 9 será validada em CI; o comportamento real deve também ser testado em equipamento físico com Android 9.

## Transferir

A versão estável mais recente, incluindo os dois APKs, está disponível nas [GitHub Releases](https://github.com/CatharsysAmaeth/DigiAutoStart/releases/latest).

Escolhe o APK correspondente à versão Android do equipamento. Consulta o [histórico de alterações](CHANGELOG.md) para conhecer a evolução do DAdA.
