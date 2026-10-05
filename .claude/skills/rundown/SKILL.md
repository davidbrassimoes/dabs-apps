---
name: rundown
description: Faz o levantamento do trabalho aberto no workspace (o "Por fazer" do CLAUDE.md, o NOTES.md de cada produto, projects/*.md, ADRs recentes, histórico git), assinala onde o estado commitado divergiu da realidade e produz um ponto de situação. Usar quando alguém pergunta onde estão as coisas, o que vem a seguir, ou invoca /rundown.
---

# Ponto de situação do workspace

**Para que serve:** levar alguém de "estive fora" a "estou a par" numa passagem, sem reler
cada `NOTES.md`. **O que não é:** um segundo sítio onde as decisões vivem. O repo continua
a fonte de verdade; isto produz uma *vista* descartável, regenerada do zero de cada vez.

## Dois tipos de achado, sempre separados

1. **Factos desatualizados.** Uma linha commitada está objetivamente errada: um item diz
   "bloqueado em X" e X foi feito há três commits. É seguro propor a correção.
2. **Decisões em aberto.** Uma pergunta real por responder. **Nunca a resolvas. Nunca a
   faças parecer fechada.** Mostra-a tal como está; é de quem decide.

Confundir os dois é pior do que não correr a skill: faria o estado parecer mais limpo do
que a realidade.

## Passos

1. **Delega o levantamento a um agente novo** (Explore ou general-purpose). Lê muitos
   ficheiros e não precisa de viver no contexto da sessão. Pede-lhe: o "Por fazer" do
   `CLAUDE.md`, a secção "Para a próxima sessão" de cada `products/*/NOTES.md`, o "Onde
   está" e "Por decidir" de cada `projects/*.md`, os ADRs dos últimos 30 dias, e
   `git log --since=30.days --oneline`. Que devolva factos com ficheiro e linha, não
   resumos.
2. **Cruza.** Para cada item aberto: há um commit, nota ou ADR posterior que o fecha ou
   contradiz? Marca-o como *desatualizado* com a prova. O resto fica como está.
3. **Escreve o ponto de situação** por produto: onde está, próximo gate, o que bloqueia,
   quem decide. Depois uma lista de *factos desatualizados* com a correção proposta, e uma
   lista de *decisões em aberto* sem opinião.
4. **Propõe as correções dos factos desatualizados** como edições ao ficheiro de origem.
   Não as apliques sem confirmação; não toques nas decisões em aberto.
