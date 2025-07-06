package main.java.dsm;

import org.oxoo2a.sim4da.Message;
import org.oxoo2a.sim4da.Node;
import org.oxoo2a.sim4da.UnknownNodeException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Basisklasse für alle DSM-Implementierungen.
 */
public abstract class AbstractDSM extends Node implements DistributedSharedMemory {

    protected final Map<String, String> localStore;
    protected final Set<String> knownNodes;
    protected final Logger logger;

    public AbstractDSM(String nodeName) {
        super(nodeName);
        this.localStore = new ConcurrentHashMap<>();
        this.knownNodes = ConcurrentHashMap.newKeySet();
        this.logger = Logger.getLogger(this.getClass().getName() + "-" + nodeName);
    }

    public void addKnownNode(String nodeId) {
        knownNodes.add(nodeId);
    }

    protected void broadcastMessage(DSMMessage message) {
        for (String nodeId : knownNodes) {
            sendMessage(message, nodeId);
        }
    }

    protected void sendMessage(DSMMessage message, String nodeId) {
        try {
            send(message, nodeId);
        } catch (UnknownNodeException e) {
            logger.warning("Knoten nicht gefunden: " + nodeId);
        }
    }

    @Override
    protected void engage() {
        logger.info("DSM-Knoten " + NodeName() + " gestartet");
    }

    public abstract void receive(Message message);
}
