package npc_fuzzy_recomendation; // Certifique-se que está no pacote correto

import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPAAgentManagement.DFAgentDescription;

/**
 * Agente que representa o Jogador.
 * Ele procura um NPC e envia um convite de parceria.
 */
public class PlayerAgent extends BaseAgent {

    @Override
    protected void setup() {
        // O BaseAgent já tem logger, rand, etc.
        logger.info("Player " + getLocalName() + " está online.");
        this.registerDF(this, "Player", "player");
        addBehaviour(handleMessages());
    }

    @Override
    protected OneShotBehaviour handleInform(ACLMessage msg) {
        return new OneShotBehaviour(this) {
            @Override
            public void action() {
                if (msg.getContent().startsWith(START)) {
                    // Execute o mesmo comportamento de buscar e convidar NPC
                    logger.info("Recebido comando START. Procurando por NPCs...");
                    DFAgentDescription[] npcAgents = searchAgentByType("NPC");

                    if (npcAgents.length > 0) {
                        AID npc = npcAgents[rand.nextInt(0, npcAgents.length)].getName();
                        logger.info("Encontrou NPC: " + npc.getLocalName() + ". Enviando convite.");

                        // Criar a mensagem de REQUEST
                        ACLMessage invitation = new ACLMessage(ACLMessage.REQUEST);
                        invitation.addReceiver(npc);

                        // Definir os stats do jogador
                        // Formato da mensagem: "PLAYER_DATA <kills> <nivel> <conquistas> <mortes> <resposta_pergunta>"
                        // Onde:
                        // - Todos os valores numéricos são enviados sem labels
                        // - A resposta da pergunta é separada por underlines
                        String[] possible_answers = { "Caio_e_volto_mais_forte",
                                "Cada_passo_e_calculado",
                                "Ganhar_e_o_unico_alvo" };

                        int player_kills = rand.nextInt(500, 1000);
                        int player_level = rand.nextInt(1, 80);
                        int player_achievements = rand.nextInt(0, 10);
                        int player_deaths = rand.nextInt(10, 100);

                        String playerStats = PLAYER_DATA + " " +
                                player_kills + " " +
                                player_level + " " +
                                player_achievements + " " +
                                player_deaths + " " +
                                possible_answers[rand.nextInt(0, possible_answers.length)];

                        invitation.setContent(playerStats);
                        
                        // Definir ID de conversa único
                        String conversationId = "npc-invite-" + myAgent.getLocalName() + "-" + System.currentTimeMillis();
                        invitation.setConversationId(conversationId);
                        
                        myAgent.send(invitation);
                        
                    } else {
                        logger.warning("Nenhum NPC encontrado.");
                    }
                } else {
                    logger.info("INFORM recebido com conteúdo: " + msg.getContent());
                }
            }
        };
    }
}