package De.Hpi.DecoAll.Debuffer.LocalNode;

import De.Hpi.DecoAll.Debuffer.Configure.Configuration;
import De.Hpi.DecoAll.Debuffer.Dao.*;
import De.Hpi.DecoAll.Debuffer.Generator.InputStream;
import De.Hpi.DecoAll.Debuffer.Message.MessageToLocal;
import De.Hpi.DecoAll.Debuffer.Message.MessageToRoot;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/*
1. windows that have gaps only increment window counter.

 */

public class OptimizerCount implements Runnable{

    private Configuration conf;
    private InputStream inputStream;
    private ConcurrentLinkedQueue<MessageToRoot> messageToRootQueue;
    private ConcurrentLinkedQueue<MessageToLocal> messageToLocalQueue;
    private ConcurrentLinkedQueue<ArrayList<Tuple>> dataQueue;

    private Query query;
    private double predictWindowSize;

    private long currentTupleCounter;
    private long totalTupleCounter;


    private long tupleCounter;
    private boolean[] operators;
    private LocalisEventHere localisEventHere;
    private Random random;

    //flags for organize()
    //there are non-decomposable function and system has to sort data anyway
    private boolean sortFLAG;
    //there are countbased window
    private boolean preserveFLAG;
    private boolean countbasedFLAG;
    private boolean maxFLAG;
    private boolean minFLAG;

    //For Deco
    private LocalWindow localWindow;


//    private long predictWindowSize;
//    private long predictWindowSizeEnd;
//    private long deltaSize;

    //For test
    private int intialTime;

    public OptimizerCount(Configuration conf, InputStream inputStream, ConcurrentLinkedQueue<MessageToRoot> messageToRootQueue,
                          ConcurrentLinkedQueue<MessageToLocal> messageToLocalQueue , ConcurrentLinkedQueue<ArrayList<Tuple>> dataQueue) {
        this.conf = conf;
        this.inputStream = inputStream;
        this.messageToRootQueue =messageToRootQueue;
        this.messageToLocalQueue =messageToLocalQueue;
        this.dataQueue =dataQueue;

        this.localWindow = new LocalWindow();
        this.localWindow.setLocalWindowCounter(1);



        this.tupleCounter = 0;
        this.operators = new boolean[conf.OPERATORS];
        this.sortFLAG = false;
        this.preserveFLAG = false;
        this.countbasedFLAG = false;
        this.maxFLAG = false;
        this.minFLAG = false;
        this.localisEventHere = new LocalisEventHere();
        this.random = new Random();

    }

    public void run() {
        //to read all queries
        queryPreProcess();
        //get predicted window size
        windowSizePreProcess();

        while(true) {
            if (!dataQueue.isEmpty()){
                ArrayList<Tuple> dataBuffer = dataQueue.poll();
                dataBuffer.forEach(tuple -> {

                    totalTupleCounter++;
                    currentTupleCounter++;
                    int isEventHere =  isEventHereCountBased();
                    processWindow(tuple, isEventHere);

                    if(isEventHere == 4){
                        try {
                            Thread.sleep(100000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                });

            }
        }
    }


    //the end tuple of a window always belongs to next window,
    private int isEventHereCountBased(){
        if(currentTupleCounter < predictWindowSize){
            //incremental aggregation
            return 1;
        }else{
            //send partial results
            return 2;
        }

    }


    private void processWindow(Tuple tuple, int isEventHere) {
        //optimizer can calculate all the queries.
        calculate(tuple, localWindow);
        switch (isEventHere){
            //1 processing
            case 1:{

                break;
            }
            //2 end the window and send results
            default :{
               MessageToRoot messageToRoot = new MessageToRoot();
               messageToRoot.setMessageType(4);
               messageToRoot.count = localWindow.count;
               messageToRoot.result = localWindow.result;
               messageToRootQueue.offer(messageToRoot);

               localWindow.count = 0;
               localWindow.result = 0;
               localWindow.localWindowCounterAdd();
                currentTupleCounter = 0;

                break;
            }
        }


    }



    void calculate(Tuple tuple, LocalWindow localWindow){
            switch (query.getFunction()) {
                case Configuration.COUNT: {
                    localWindow.count++;
                    break;
                }
                case Configuration.SUM: {
                    localWindow.result += tuple.DATA;
                    break;
                }
                case Configuration.AVERAGE: {
                    localWindow.count++;
                    localWindow.result += tuple.DATA;
                    break;
                }
                case Configuration.MAX: {
                    localWindow.result = Math.max(localWindow.result, tuple.DATA);
                    break;
                }
                case Configuration.MIN: {
                    localWindow.result = Math.min(localWindow.result, tuple.DATA);
                    break;
                }
                default:
                    break;
           }
    }




    private void queryPreProcess(){
        while(true){
            if(!messageToLocalQueue.isEmpty()){
                this.query = messageToLocalQueue.poll().query;
                break;
            }else{
                try {
                    Thread.sleep(conf.queryWait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void windowSizePreProcess(){
        MessageToRoot messageToRoot = new MessageToRoot();
        messageToRoot.setMessageType(2);
        messageToRoot.eventRate = inputStream.getEventRates();
        sendMessage(messageToRoot);
        while(true){
            if(!messageToLocalQueue.isEmpty()){
                MessageToLocal messageToLocal = messageToLocalQueue.poll();
                this.predictWindowSize = messageToLocal.localWindowSize;
                break;
            }else{
                try {
                    Thread.sleep(conf.queryWait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(MessageToRoot messageToRoot){
        messageToRootQueue.offer(messageToRoot);
    }

}
