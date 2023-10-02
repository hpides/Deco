package De.Hpi.DecoAll.Debuffer.LocalNode;

import De.Hpi.DecoAll.Debuffer.Configure.Configuration;
import De.Hpi.DecoAll.Debuffer.Dao.Tuple;
import De.Hpi.DecoAll.Debuffer.Message.MessageToRoot;
import org.msgpack.MessagePack;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LocalPublishMessage implements Runnable{

    private Configuration conf;
    private ConcurrentLinkedQueue<MessageToRoot> messageToRootQueue;
    private ZMQ.Socket socketPub;

    public LocalPublishMessage(ConcurrentLinkedQueue<MessageToRoot> messageToRootQueue, ZMQ.Socket socketPub, Configuration conf) {
        this.conf =conf;
        this.messageToRootQueue =messageToRootQueue;
        this.socketPub = socketPub;
    }

    public void run() {
//        System.out.println("Starting RequestThread ----localNode");
        MessagePack msgpack = new MessagePack();

//        int infoCounter = 0;
//        float infoAll = 0;

//        Long networkOverhead = 0l;
//        long begintime = System.currentTimeMillis();
        long endtime = System.currentTimeMillis();
        while (true) {
            if(!messageToRootQueue.isEmpty()) {
                MessageToRoot messageToRoot = (MessageToRoot) messageToRootQueue.poll();
                messageToRoot.setNodeId(conf.getNodeId());

                try {
                    byte[] raw = msgpack.write(messageToRoot);
//                    System.out.println(raw.length);
//                    for(int i = 0; i < conf.queryModes; i++)

                    socketPub.send(raw);

                    if(conf.DEBUGMODE_LOCAL) {
//                        networkOverhead += getNetworkOverhead(raw.length);
                        if (System.currentTimeMillis() - endtime >= conf.BenchMarkDebugFrequency) {
//                        if (System.currentTimeMillis() - endtime > 1000000) {
                                endtime = System.currentTimeMillis();
                                System.out.println("LocalNode--" + conf.getNodeId() + "--sending message----"
//                                        + "  WindowCounter:  " + messageResult.windowCollection.sliceCounter
//                                        + (!messageResult.windowCollection.windowList.isEmpty() ?
//                                        ( "  QueryId:  " + messageResult.windowCollection.windowList.get(0).queryId
//                                        + "  WindowId:  " + messageResult.windowCollection.windowList.get(0).windowId
                                        + "  nodeId:  " + messageToRoot.getNodeId()
                                        + "  Type:  " + messageToRoot.getMessageType()
//                                        + "  result:  " + 0
//                                        + "  time:  " + 0
//                                        + "  event:  " + tuple.EVENT
//                                        + "  WindowList:  " + messageResult.windowCollection.windowList.size()
//                                        + "  TupleList:  " + (messageResult.windowCollection.tuples != null ?
//                                                messageResult.windowCollection.tuples.size() : 0)
//                                        + "  Queue:  " + intermediateResultQueue.size()
//                                        + "  Latency:  " + latencyAll / latencyCounter
//                                        + "  Slices:  " + infoAll / infoCounter
//                                        + "  Throughput:  " + messageResult.window.tupleCounter / ((endtime - begintime) / 1000.0)
//                                        + "  NetworkOverhead:  " + networkOverhead
//                                        + "  Allcounter:  " + messageResult.window.tupleCounter
//                                        + "  Time:  " + (endtime - begintime) / 1000.0
//                                        + "  GCTime:  " + getGarbageCollectionTime()
//                                        + "  GC/Time:  " + (double) getGarbageCollectionTime() / (endtime - begintime)
                                );

                            }
                        }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

}
