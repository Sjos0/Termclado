# Termclado v1.9.7

Correções da inspeção de código: cada item abaixo estava registrado como issue, e foi confirmado no código antes de corrigir.

## Correções
- **Backspace com seleção** (#35): com texto selecionado, o backspace apaga o intervalo inteiro, e o cursor fica no início dele.
- **Emoji apagado sem tofu** (#38, #41): o backspace agora apaga o emoji (e sequências com tom de pele ou ZWJ) inteiro, sem deixar o losango com "?".
- **Palavras longas na barra** (#36): cada sugestão reduz a fonte só até caber no seu espaço, sem cortar nem sobrepor.
- **Letras das teclas** (#37): maiores e proporcionais à altura da tecla; reduzidas só quando a largura passaria do limite.
- **Ícone do shift** (#39): seta com contorno quando o shift está desligado, e preenchida quando o caps lock está travado.
- **README** (#40): não fixa mais um número de versão que ficava desatualizado.

## Ainda não está nesta versão
- Aprendizado de palavras (#43), autopreenchimento de senhas e e-mails na barra (#44) e IA local por bigramas (#45): planejados, cada um com a sua issue.
