# Termclado v1.9.4

Visual alinhado ao Gboard, sugestões de emoji e correção de um toque indevido na barra.

## Novidades

- **Barra com layout do Gboard:** grade à esquerda, sugestões no centro e microfone em círculo à direita. A barra não muda mais de layout ao terminar uma palavra.
- **Ícone da grade igual ao do Gboard** (quatro quadrados arredondados). O conteúdo da grade aberta não mudou.
- **Captura de tela na barra:** quando há uma captura recente e a permissão de fotos foi concedida, aparece uma pílula "Captura de tela" com miniatura. Ao tocar, a imagem é enviada ao app que você está usando, se ele aceitar imagens. Ajuste em Configurações → "Capturas de tela na barra".
- **Emojis nas sugestões:** palavras com emoji correspondente mostram o emoji ao lado das palavras, como no Gboard (ex.: "teclado" sugere ⌨ e 🎹).
- **Teclas no estilo Gboard:** "?123" e enter em pílula; enter em teal; demais teclas com cantos suaves.
- **Ícones originais do Material** em todo o teclado e nas telas de ajustes, no lugar dos desenhos feitos à mão.

## Correções

- **Toque indevido na barra:** ao tocar numa sugestão e logo em seguida num ícone, o app não abre mais por engano. Os ícones de ajustes e tradução só aparecem dentro da linha de ferramentas, aberta pelo botão da grade.
- **Tradução:** mostra o aviso "Tradução ainda não disponível" em vez de abrir o app.
- **Ícones com fundo sólido** (quadrados brancos) foram corrigidos antes da release.

## Observações

- Ainda não há aprendizado de palavras, autopreenchimento de senhas e e-mails, nem IA local por bigramas. Estão planejados.
- O ícone "Tradução" e os ícones da linha de ferramentas (cursor, área de transferência, ajustes) continuam como antes, conforme combinado.
- Testes: unitários no CI (incluindo as sugestões de emoji), testes do motor Rust e captura de tela no emulador Android 14.
