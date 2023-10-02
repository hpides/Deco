package De.Hpi.Scotty.source.slicing.slice;


import De.Hpi.Scotty.source.slicing.WindowManager;
import De.Hpi.Scotty.source.slicing.state.AggregateState;
import De.Hpi.Scotty.source.state.StateFactory;

public class EagerSlice<InputType, ValueType> extends AbstractSlice<InputType, ValueType> {

    private final AggregateState<InputType> state;

    public EagerSlice(StateFactory stateFactory, WindowManager windowManager, long startTs, long endTs, long startC, long endC, Type type) {
        super(startTs, endTs,startC, endC, type);
        this.state = new AggregateState<InputType>(stateFactory, windowManager.getAggregations(), null);
    }

    @Override
    public AggregateState getAggState() {
        return state;
    }

    @Override
    public void addElement(InputType element, long ts) {
        super.addElement(element, ts);
        state.addElement(element);
    }


}
