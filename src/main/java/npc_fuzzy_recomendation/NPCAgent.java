package npc_fuzzy_recomendation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

public class NPCAgent extends BaseAgent {

	private static final long serialVersionUID = 1L;

	private static Map<String, Integer>  operations = Collections.synchronizedMap(new HashMap<>());
	private static Set<AID> timedOutAgents = Collections.synchronizedSet(new HashSet<AID>());
	private static Map<String, ArrayList<AID>>  operationsRequested = Collections.synchronizedMap(new HashMap<>());
	private static Set<String> operationsSent = Collections.synchronizedSet(new HashSet<String>());

	@Override
	protected void setup() {
		logger.log(Level.INFO, "I'm the NPC: " + this.getLocalName());
		this.registerDF(this, "NPC", "npc");

		addBehaviour(handleMessages());
	}
	
	// Mapa para armazenar os resultados de cada estratégia
	private Map<String, Double> strategyResults = new HashMap<>();
	
	@Override
	protected OneShotBehaviour handleInform(ACLMessage msg) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;
			
			public void action() {
				if (msg.getContent().startsWith(INFORM) && msg.getContent().contains(PLAYER_DATA)) {
					// Processar resposta de um subordinado
					ArrayList<String> msgContent = new ArrayList<>(Arrays.asList(msg.getContent().split(" ")));
					String strategy = msgContent.get(1); // FUZZY, LLM, ou STATISTIC
					
					// Extrair o resultado numérico (assumindo que é o último número na mensagem)
					ArrayList<String> recvData = parseData(msg);
					if (!recvData.isEmpty()) {
						Double result = Double.parseDouble(recvData.get(1)); // Pega o segundo resultado
						strategyResults.put(strategy, result);
						
						logger.log(Level.INFO, String.format("%s Recebido resultado da estratégia %s: %.2f", 
							getLocalName(), strategy, result));
					}
					
					// Remover a operação da lista de pendentes
					operations.remove(strategy);
					timedOutAgents.remove(msg.getSender());
					operationsSent.remove(strategy);
					
					// Agradecer ao subordinado
					sendMessage(msg.getSender().getLocalName(), ACLMessage.INFORM, THANKS);
					
					// Verificar se todas as estratégias responderam
					if (operations.isEmpty()) {
						// Todas as estratégias responderam, hora de tomar a decisão final
						double finalDecision = makeFinalDecision();
						
						// Encontrar o Player para enviar a resposta
						DFAgentDescription[] players = searchAgentByType("Player");
						logger.log(Level.INFO, String.format("%s Procurando por agentes Player. Encontrados: %d", 
							getLocalName(), players.length));
							
						if (players == null || players.length == 0) {
							logger.log(Level.WARNING, String.format("%s Nenhum agente Player encontrado no DF!", getLocalName()));
							return;
						}
						
						if (players.length > 0) {
							AID playerAID = players[0].getName();
							ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
							reply.addReceiver(playerAID);
							
							// Determinar aceitação baseado no threshold (por exemplo, 70%)
							boolean accepted = finalDecision >= 70.0;
							String decision = accepted ? "ACEITO" : "RECUSADO";
							
							// Criar resposta no formato PLAYER_DATA
							StringBuilder response = new StringBuilder();
							response.append(PLAYER_DATA).append(" RESPOSTA ");
							response.append(decision).append(" ");
							response.append(String.format("%.2f", finalDecision));
							
							// Adicionar as avaliações individuais
							for (Map.Entry<String, Double> entry : strategyResults.entrySet()) {
								response.append(" ").append(entry.getKey())
									   .append(":").append(String.format("%.2f", entry.getValue()));
							}
							
							reply.setContent(response.toString());
							
							// Log detalhado para debug
							logger.log(Level.INFO, "\nDecisão final do NPC para o jogador:" +
								"\n----------------------------------------" +
								"\nStatus: " + decision +
								"\nScore Final: " + String.format("%.2f%%", finalDecision) +
								"\nAvaliações individuais:" +
								"\n- FUZZY: " + String.format("%.2f%%", strategyResults.getOrDefault("FUZZY", 0.0)) +
								"\n- LLM: " + String.format("%.2f%%", strategyResults.getOrDefault("LLM", 0.0)) +
								"\n- STATISTIC: " + String.format("%.2f%%", strategyResults.getOrDefault("STATISTIC", 0.0)) +
								"\n----------------------------------------");
							
							send(reply);
							
							// Limpar resultados para próxima rodada
							strategyResults.clear();
						}
					}
				} else if (msg.getContent().startsWith(START)) {
					// Iniciar nova rodada de análise
					logger.log(Level.INFO, String.format("%s MANAGER AGENT RECEIVED A START!", getLocalName()));
					strategyResults.clear(); // Limpar resultados anteriores
					
					for (String opp : operations.keySet()) {
						searchSubordinatesByOperation(opp);
					}
					
					logger.log(Level.INFO, String.format("%s SENT CFP MESSAGE TO WORKERS!", getLocalName()));
				} else if (msg.getContent().startsWith(THANKS)) {
					logger.log(Level.INFO, String.format("%s RECEIVED THANKS FROM %s!", 
						getLocalName(), msg.getSender().getLocalName()));
				} else {
					logger.log(Level.INFO,
							String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG,
									msg.getSender().getLocalName()));
				}
			}
		};
	}
	
	/**
	 * Calcula a decisão final baseada nos resultados de todas as estratégias.
	 * Implementa uma lógica de "melhor de três" ponderada.
	 */
	private double makeFinalDecision() {
		if (strategyResults.isEmpty()) {
			return 0.0;
		}
		
		// Pesos para cada estratégia
		Map<String, Double> weights = new HashMap<>();
		weights.put("FUZZY", 0.4);    // 40% peso para Fuzzy
		weights.put("LLM", 0.35);     // 35% peso para LLM
		weights.put("STATISTIC", 0.25); // 25% peso para Estatística
		
		double weightedSum = 0.0;
		double totalWeight = 0.0;
		
		// Calcular média ponderada
		for (Map.Entry<String, Double> entry : strategyResults.entrySet()) {
			String strategy = entry.getKey();
			Double result = entry.getValue();
			Double weight = weights.getOrDefault(strategy, 0.33); // Peso padrão se não especificado
			
			weightedSum += result * weight;
			totalWeight += weight;
		}
		
		// Retornar média ponderada normalizada
		return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
	}
    
	@Override
	protected OneShotBehaviour handleCfp(ACLMessage msg) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action() {
				String content = msg.getContent();
				if(msg.getPerformative() == ACLMessage.PROPOSE) {
					if (content.startsWith("CAN_DO")) {
						// Extract the operation from the message
						String operation = content.split(" ")[1];
						
						if (operations.containsKey(operation) && !operationsSent.contains(operation)) {
							// We need this operation and haven't sent data to anyone yet
							operationsSent.add(operation);
							
							// Create acceptance message with the player data
							ACLMessage reply = msg.createReply();
							reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
							logger.log(Level.INFO, "Preparing data to send for operation: " + operation);
							// Preparar a mensagem com os dados do jogador
							StringBuilder dataMsg = new StringBuilder();
							dataMsg.append(operation).append(" ").append(PLAYER_DATA);
							for (String data : workingData) {
								dataMsg.append(" ").append(data);
							}
							reply.setContent(dataMsg.toString());
							logger.log(Level.INFO, "Sending data: " + dataMsg.toString());
							
							// Send acceptance and data
							send(reply);
							
							// Set up timeout for this agent's response
							addBehaviour(timeoutBehaviour(msg.getSender(), operation, TIMEOUT_LIMIT));
							
							logger.log(Level.INFO, String.format("%s ACCEPTED PROPOSAL FROM %s FOR %s", 
								getLocalName(), msg.getSender().getLocalName(), operation));
								
						} else {
							// Either we don't need this operation anymore or already assigned it
							ACLMessage reply = msg.createReply();
							reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
							reply.setContent(String.format("OPERATION %s NOT NEEDED OR ALREADY ASSIGNED", operation));
							send(reply);
							
							logger.log(Level.INFO, String.format("%s REJECTED PROPOSAL FROM %s FOR %s (not needed/already assigned)", 
								getLocalName(), msg.getSender().getLocalName(), operation));
						}
					} else {
						logger.log(Level.WARNING, String.format("%s Unexpected CFP response format from %s: %s", 
							getLocalName(), msg.getSender().getLocalName(), content));
					}
				}
			}
		};
	}

	@Override
	protected OneShotBehaviour handleRefuse (ACLMessage msg) {
		return new OneShotBehaviour(this){
			private static final long serialVersionUID = 1L;

			public void action() {

				if ( msg.getContent().startsWith("OPERATION") ) {
					String [] splittedMsg = msg.getContent().split(" ");

					if ( !operations.keySet().contains(splittedMsg[1]) ) {
						logger.log(Level.WARNING,
							String.format("%s %s %s %s %s", ANSI_YELLOW, getLocalName(), ": OPERATION NOT NEEDED SENT FROM",
									msg.getSender().getLocalName(), ANSI_RESET));
					} else {
						searchSubordinatesByOperation(splittedMsg[1], Arrays.asList(msg.getSender()));
					}
				} else {
					logger.log(Level.INFO,
							String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG,
									msg.getSender().getLocalName()));
				}

			}
		};
	}

	@Override
	protected WakerBehaviour timeoutBehaviour(AID requestedAgent, String requestedOperation, long timeout) {
		return new WakerBehaviour(this, timeout) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onWake() {
				if ( operations.containsKey(requestedOperation) ) {
					logger.log(Level.WARNING,
						String.format("%s Agent %s timed out! %s", ANSI_YELLOW, requestedAgent.getLocalName(), ANSI_RESET));

					operationsSent.remove(requestedOperation);

					timedOutAgents.add(requestedAgent);
					searchSubordinatesByOperation(requestedOperation, new ArrayList<>(timedOutAgents));
				}
			}
		};
	}
	

    @Override
    protected OneShotBehaviour handleRequest(ACLMessage msg) {
        final NPCAgent agent = this;
        return new OneShotBehaviour(this) {
            @Override
            public void action() {
                if (msg.getContent().startsWith(PLAYER_DATA)) {
                    logger.info("Recebido dados do jogador. Iniciando análise com múltiplas estratégias.");
                    
                    // Armazenar dados do jogador
                    workingData = parseData(msg);
                    logger.info("Dados do jogador recebidos: " + workingData);
                    
                    // Array com as estratégias disponíveis
                    String[] strategies = {"FUZZY", "LLM", "STATISTIC"};
                    
                    // Limpar dados antigos
                    operations.clear();
                    timedOutAgents.clear();
                    operationsRequested.clear();
                    operationsSent.clear();
                    
                    // Registrar operações que precisamos
                    for (String strategy : strategies) {
                        operations.put(strategy, 1);
                    }
                    
                    // Procurar subordinados para cada estratégia
                    for (String strategy : strategies) {
                        logger.info("Buscando subordinados para estratégia: " + strategy);
                        searchSubordinatesByOperation(strategy);
                        
                        // Configurar timeout para cada operação
                        if (!operationsRequested.get(strategy).isEmpty()) {
                            AID worker = operationsRequested.get(strategy).get(0);
                            addBehaviour(timeoutBehaviour(worker, strategy, TIMEOUT_LIMIT));
                        }
                    }
                    
                    // Aguardar todas as respostas (handleInform lidará com as respostas)
                    // O comportamento handleInform será chamado para cada resposta recebida
                    // e irá coletar os resultados
                    
                    // Quando todas as respostas forem recebidas (operations estiver vazio),
                    // o handleInform enviará a resposta final baseada no "melhor de três"
                    
                    // Enviar resposta inicial ao jogador
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Análise iniciada com " + strategies.length + " estratégias diferentes.");
                    send(reply);
                    
                } else {
                    logger.warning("Mensagem REQUEST recebida sem dados do jogador");
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("Dados do jogador não encontrados na mensagem");
                    send(reply);
                }
            }
        };
    }

	private void searchSubordinatesByOperation(String opp) {
		ArrayList<DFAgentDescription> foundWorkers = new ArrayList<>(
			Arrays.asList(searchAgentByType(opp)));

		ArrayList<AID> workersArray = new ArrayList<>(foundWorkers.stream().map(val -> val.getName()).toList());

		if (operationsRequested.get(opp) == null){
			operationsRequested.put(opp, workersArray);
		} else {
			workersArray.addAll(operationsRequested.get(opp));
			operationsRequested.put(opp, workersArray);
		}

		foundWorkers.forEach(ag -> 
			sendMessage(ag.getName().getLocalName(), ACLMessage.CFP,
				String.format("%s", opp))
		);
	}

	private void searchSubordinatesByOperation(String opp, List<AID> unwantedAgents) {
		ArrayList<DFAgentDescription> foundWorkers = new ArrayList<>(
			Arrays.asList(searchAgentByType(opp)));

		ArrayList<AID> workersArray = new ArrayList<>(foundWorkers.stream().map(val -> val.getName()).toList());

		for ( AID notThisAgent : unwantedAgents ) 
			workersArray.remove(notThisAgent);

		
		if (operationsRequested.get(opp) == null){
			operationsRequested.put(opp, workersArray);
		} else {
			workersArray.addAll(operationsRequested.get(opp));
			operationsRequested.put(opp, workersArray );
		}

		workersArray.forEach(ag -> 
			sendMessage(ag.getLocalName(), ACLMessage.CFP, String.format("%s", opp))
		);
	}
}