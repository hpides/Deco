package De.Hpi.DecoAll.DecoAsy.RootNode;

import De.Hpi.DecoAll.DecoAsy.Configure.Configuration;
import De.Hpi.DecoAll.DecoAsy.Dao.*;
import De.Hpi.DecoAll.DecoAsy.Message.MessageToLocal;
import De.Hpi.DecoAll.DecoAsy.Message.MessageToRoot;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class RootComputationEngineDecentral implements Runnable {

    private Configuration conf;
    private ConcurrentLinkedQueue<Query> queryQueue;
    private ConcurrentLinkedQueue<Finalresult> resultQueue;
    private ConcurrentLinkedQueue<MessageToRoot> messageToRootQueue;
    private ConcurrentLinkedQueue<MessageToLocal> messageToLocalQueue;
    private Query query;
    private ArrayList<LocalEventRate> localEventRates;
    private int localEventRatesCounter;
    //0 in total, 1 node 1, 2 node 2
    private double[] localWindowSizes;

    private RootWindow rootWindow;
    private ArrayList<PartialResult> localPartialResultList;
    private int localPartialResultsCounter;
    //1 initialization(local), 2 prediction, 3 calculation(local)
    private int stepFlag;

    private int currentSliceId;
    private long tupleCounter;
    private boolean countBasedFlag;
    private boolean timeBasedNonDecomposableFlag;

    //for debug
//    private long latencyOverall;
//    private long latencyCounter;

    RootComputationEngineDecentral(ConcurrentLinkedQueue<MessageToRoot> messageToRootQueue,
                                   ConcurrentLinkedQueue<MessageToLocal> messageToLocalQueue,
                                   Configuration conf,
                                   ConcurrentLinkedQueue<Finalresult> resultQueue,
                                   ConcurrentLinkedQueue<Query> queryQueue){
        this.conf = conf;
        this.resultQueue =resultQueue;
        this.messageToRootQueue =messageToRootQueue;
        this.messageToLocalQueue =messageToLocalQueue;
        this.queryQueue = queryQueue;
        this.localEventRates = new ArrayList<>();
        this.localWindowSizes = new double[conf.localNumber + 1];

        this.rootWindow = new RootWindow();
        this.rootWindow.setWindowWaitCounter(1);
        this.localPartialResultList = new ArrayList<>();


        this.currentSliceId = 0;
        this.tupleCounter = 0;
        this.countBasedFlag = false;
        this.timeBasedNonDecomposableFlag = false;

        //for debug
//        this.latencyOverall = 0;
//        this.latencyCounter = 0;
        initial();

    }

    void initial(){
        //initial local event rate list
        for(int i = 1; i <= conf.localNumber; i++){
            LocalEventRate localEventRate = new LocalEventRate();
            localEventRate.setNodeId(i);
            localEventRate.setEventRates(conf.eventGenerateRate);
            localEventRate.counterForStep = 0;
            //default value is 10
            localEventRate.previousCorrectionFactorList = new LinkedList<>();
            localEventRates.add(localEventRate);

            PartialResult partialResult = new PartialResult();
            partialResult.setNodeId(i);
            partialResult.count = 0;
            partialResult.result = 0;
            partialResult.counterForStep = 0;
            localPartialResultList.add(partialResult);
        }
        localEventRatesCounter = 0;
    }

    public void run() {
        queryPreProcess();
        while (true) {
            if (!messageToRootQueue.isEmpty()) {
                MessageToRoot messageToRoot = messageToRootQueue.poll();
                switch (messageToRoot.getMessageType()){
                    //get event rates
                    case 2:{
                        localEventRates.forEach(localEventRate -> {
                            if(localEventRate.getNodeId() == messageToRoot.getNodeId() && localEventRate.counterForStep == 0) {
                                localEventRate.setEventRates(messageToRoot.eventRate);
                                localEventRate.counterForStep = 1;
                                localEventRatesCounter++;
                            }
                        });
                        //we get all event rates
                        if(localEventRatesCounter == conf.localNumber){
                            calculateLocalWindowSize();
//                            System.out.println(localEventRates.get(0).getEventRates() + "  " + localEventRates.get(0).counterForStep);
//                            System.out.println(localEventRates.get(1).getEventRates() + "  " + localEventRates.get(1).counterForStep);
//                            System.out.println(localEventRatesCounter);
                        }
                        break;
                    }
                    //get event rates, partial results and buffer
                    case 5:{
                        localPartialResultList.forEach(partialResult -> {
                            if (partialResult.getNodeId() == messageToRoot.getNodeId() && partialResult.counterForStep == 0) {
                                partialResult.count = messageToRoot.count;
                                partialResult.result = messageToRoot.result;
                                partialResult.bufferTupleListStart = messageToRoot.bufferTupleListStart;
                                partialResult.bufferTupleListEnd = messageToRoot.bufferTupleListEnd;
                                partialResult.eventRate = messageToRoot.eventRate;
                                partialResult.counterForStep = 1;
                                localPartialResultsCounter++;
                            }
                        });
                        if(localPartialResultsCounter == conf.localNumber){
                            calculateResultAndBuffer();
                        }
                        break;
                    }
                    //get results (4)
                    default: {
//                        System.out.println(messageToRoot.getNodeId() + "   " + messageToRoot.count+ "   " + messageToRoot.result);
                        localPartialResultList.forEach(partialResult -> {
                            if (partialResult.getNodeId() == messageToRoot.getNodeId() && partialResult.counterForStep == 0) {
                                partialResult.count = messageToRoot.count;
                                partialResult.result = messageToRoot.result;
                                partialResult.counterForStep = 1;
                                localPartialResultsCounter++;
                            }
                        });
                        if(localPartialResultsCounter == conf.localNumber){
                            calculateResult();
                        }
                        break;
                    }
                }
            }
        }
    }

    private void calculateLocalWindowSize(){
        localEventRates.forEach(localEventRate -> {
            localEventRate.counterForStep = 0;
            localWindowSizes[0] += localEventRate.getEventRates();
            localWindowSizes[localEventRate.getNodeId()] = localEventRate.getEventRates();
        });
        //calculate local window size, not smart but works
        localWindowSizes[1] = query.getRange();
        if(conf.localNumber >= 2) {
            for (int i = 2; i <= conf.localNumber; i++) {
                localWindowSizes[i] = (int)(localWindowSizes[i] / localWindowSizes[0] * query.getRange());
                localWindowSizes[1] -= localWindowSizes[i];
            }
        }
        //send all local window size back
        for (int i = 0; i < conf.localNumber; i++) {
            localEventRates.get(i).previousPredictedWindowSize = localEventRates.get(i).predictedWindowSize;
            localEventRates.get(i).predictedWindowSize =  localWindowSizes[i+1];
            localEventRates.get(i).previousCorrectionFactorList.addLast(Math.abs(localEventRates.get(i).predictedWindowSize -
                    localEventRates.get(i).previousPredictedWindowSize));
//            localEventRates.get(i).previousCorrectionFactor =  localEventRates.get(i).correctionFactor;
//            localEventRates.get(i).previousCorrectionFactorList.addLast(Math.abs(localEventRates.get(i).predictedWindowSize -
//                    localEventRates.get(i).previousPredictedWindowSize));
//            localEventRates.get(i).correctionFactor = (localEventRates.get(i).correctionFactor * conf.InitializationSteps -
//                    localEventRates.get(i).previousCorrectionFactorList.getFirst() +
//                    localEventRates.get(i).previousCorrectionFactorList.getLast()) / conf.InitializationSteps;
//            localEventRates.get(i).previousCorrectionFactorList.removeFirst();

        }
        //send all local window size
        // there are 10 windows already
        if(localEventRates.get(0).previousCorrectionFactorList.size() == conf.InitializationSteps){
            for (int i = 0; i < conf.localNumber; i++) {
                Double temp = localEventRates.get(i).previousCorrectionFactorList.getLast();
                localEventRates.get(i).previousCorrectionFactorList.removeFirst();
                localEventRates.get(i).previousCorrectionFactorList.addFirst(temp);
                int finalI = i;
                localEventRates.get(i).previousCorrectionFactorList.forEach(windowSizeTemp -> {
                    localEventRates.get(finalI).correctionFactor += windowSizeTemp;
                });
                localEventRates.get(finalI).correctionFactor = (int)localEventRates.get(finalI).correctionFactor / 10.0;


                MessageToLocal messageToLocal = new MessageToLocal();
                messageToLocal.setNodeId(i+1);
                messageToLocal.setMessageType(3);
                messageToLocal.localWindowSize = localEventRates.get(i).predictedWindowSize;
                messageToLocal.correctionFactor = localEventRates.get(i).correctionFactor;
                sendMessage(messageToLocal);
            }
            System.out.println("-------------After Initialization--------------");
        }
        //clear
        localEventRatesCounter = 0;
        localWindowSizes[0] = 0;
    }

    private void calculateResultAndBuffer(){
        localPartialResultList.forEach(partialResult -> {
            partialResult.counterForStep = 0;
            localWindowSizes[0] += partialResult.eventRate;
            localWindowSizes[partialResult.getNodeId()] = partialResult.eventRate;
        });
        //calculate local window size, not smart but works
        localWindowSizes[1] = query.getRange();
        if(conf.localNumber >= 2) {
            for (int i = 2; i <= conf.localNumber; i++) {
                localWindowSizes[i] = (int)(localWindowSizes[i] / localWindowSizes[0] * query.getRange());
                localWindowSizes[1] -= localWindowSizes[i];
            }
        }

        //the prediction is correct, so lets move to next window
        if(verifiedPrediction()){
            calculateResult();

            for (int i = 0; i < conf.localNumber; i++) {
                localEventRates.get(i).previousPredictedWindowSize = localEventRates.get(i).predictedWindowSize;
                localEventRates.get(i).predictedWindowSize =  localWindowSizes[i+1];
                localEventRates.get(i).previousCorrectionFactorList.addLast(Math.abs(localEventRates.get(i).predictedWindowSize -
                        localEventRates.get(i).previousPredictedWindowSize));
                localEventRates.get(i).correctionFactor = (int)Math.abs(localEventRates.get(i).correctionFactor * conf.InitializationSteps -
                        localEventRates.get(i).previousCorrectionFactorList.getFirst() +
                        localEventRates.get(i).previousCorrectionFactorList.getLast()) / conf.InitializationSteps;
                localEventRates.get(i).previousCorrectionFactorList.removeFirst();

                MessageToLocal messageToLocal = new MessageToLocal();
                messageToLocal.setNodeId(i+1);
                messageToLocal.setMessageType(3);
                messageToLocal.localWindowSize = localEventRates.get(i).predictedWindowSize;
                messageToLocal.correctionFactor = localEventRates.get(i).correctionFactor;
                sendMessage(messageToLocal);

//                localPartialResultList.get(i).bufferTupleList = null;
            }
            //clear
            localEventRatesCounter = 0;
            localPartialResultsCounter = 0;
            localWindowSizes[0] = 0;
        //the prediction is wrong
        }else{
            for (int i = 0; i < conf.localNumber; i++) {
                localEventRates.get(i).previousPredictedWindowSize = localEventRates.get(i).predictedWindowSize;
                localEventRates.get(i).predictedWindowSize =  localWindowSizes[i+1];
                localEventRates.get(i).previousCorrectionFactorList.addLast(Math.abs(localEventRates.get(i).predictedWindowSize -
                        localEventRates.get(i).previousPredictedWindowSize));
                localEventRates.get(i).correctionFactor = (int)Math.abs(localEventRates.get(i).correctionFactor * conf.InitializationSteps -
                        localEventRates.get(i).previousCorrectionFactorList.getFirst() +
                        localEventRates.get(i).previousCorrectionFactorList.getLast()) / conf.InitializationSteps;
                localEventRates.get(i).previousCorrectionFactorList.removeFirst();

                MessageToLocal messageToLocal = new MessageToLocal();
                messageToLocal.setNodeId(i + 1);
                messageToLocal.setMessageType(6);
                messageToLocal.localWindowSize = localEventRates.get(i).predictedWindowSize;
                messageToLocal.correctionFactor = localEventRates.get(i).correctionFactor;
                sendMessage(messageToLocal);
            }
            //clear
            localEventRatesCounter = 0;
            localPartialResultsCounter = 0;
            localWindowSizes[0] = 0;
        }
    }

    private boolean verifiedPrediction(){
        AtomicBoolean flag = new AtomicBoolean(false);
        localPartialResultList.forEach(partialResult -> {
            if (localWindowSizes[partialResult.getNodeId()] >
                    (localEventRates.get(partialResult.getNodeId()-1).predictedWindowSize -
                            localEventRates.get(partialResult.getNodeId()-1).correctionFactor) &&
                localWindowSizes[partialResult.getNodeId()] <=
                        (localEventRates.get(partialResult.getNodeId()-1).predictedWindowSize +
                                localEventRates.get(partialResult.getNodeId()-1).correctionFactor)){
                flag.set(true);
            }
//            System.out.println(localWindowSizes[partialResult.getNodeId()]);
//            System.out.println(localEventRates.get(partialResult.getNodeId()-1).predictedWindowSize);
//            System.out.println(localEventRates.get(partialResult.getNodeId()-1).correctionFactor);
        });
//        return flag.get();
        return true;
    }

    private void calculateResult(){
        //calculate
        Finalresult finalresult = new Finalresult();
        finalresult.setWindowId(rootWindow.getWindowId());
        localPartialResultList.forEach(partialResult -> {
            partialResult.counterForStep = 0;
            calculate(partialResult, finalresult);
            calculateBuffer(partialResult, finalresult, (int) (localWindowSizes[partialResult.getNodeId()] -
                                localEventRates.get(partialResult.getNodeId()-1).predictedWindowSize +
                                localEventRates.get(partialResult.getNodeId()-1).correctionFactor));
        });
        //send result
        resultQueue.offer(finalresult);
        //clear
        localPartialResultsCounter = 0;
        rootWindow.addWindowId();
    }

    void calculate(PartialResult partialResult, Finalresult finalresult){
        switch (query.getFunction()) {
            case Configuration.COUNT: {
                finalresult.count += partialResult.count;
                break;
            }
            case Configuration.SUM: {
                finalresult.result += partialResult.result;
                break;
            }
            case Configuration.AVERAGE: {
                finalresult.count += partialResult.count;
                finalresult.result += partialResult.result;
                break;
            }
            case Configuration.MAX: {
                finalresult.result = Math.max(finalresult.result, partialResult.result);
                break;
            }
            case Configuration.MIN: {
                finalresult.result = Math.min(finalresult.result, partialResult.result);
                break;
            }
            default:
                break;
        }
    }

    void calculateBuffer(PartialResult partialResult, Finalresult finalresult, int bufferSize){
        //this is only for test
        partialResult.bufferTupleListStart.addAll(partialResult.bufferTupleListEnd);
        int index = Math.min(bufferSize, partialResult.bufferTupleListStart.size());
        for(int i = 0; i < index; i++) {
            Tuple tuple = partialResult.bufferTupleListStart.get(i);
            switch (query.getFunction()) {
                case Configuration.COUNT: {
                    finalresult.count ++;
                    break;
                }
                case Configuration.SUM: {
                    finalresult.result += tuple.DATA;
                    break;
                }
                case Configuration.AVERAGE: {
                    finalresult.count ++;
                    finalresult.result += tuple.DATA;
                    break;
                }
                case Configuration.MAX: {
                    finalresult.result = Math.max(finalresult.result, tuple.DATA);
                    break;
                }
                case Configuration.MIN: {
                    finalresult.result = Math.min(finalresult.result, tuple.DATA);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void sendMessage(MessageToLocal messageToLocal){
        messageToLocalQueue.offer(messageToLocal);
    }

    private void queryPreProcess(){
        while(true){
            if(!queryQueue.isEmpty()){
                this.query = queryQueue.poll();
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


}
