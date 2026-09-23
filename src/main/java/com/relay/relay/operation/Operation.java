package com.relay.relay.operation;

public class Operation {
    private final String id;
    private OperationState state;

    public Operation(String id) {
        this.id = id;
        this.state = OperationState.PENDING;
    }

    public OperationState getState() {
        return state;
    }
    public String getId() {
        return id;
    }

    public void transitionTo(OperationState newState){
        if(!isValidTransition(this.state, newState)){
            throw new IllegalStateException(
                    "Invalid transition"
                    + this.state
                    + "-.>"
                    + newState
            );
        }
        this.state = newState;
    }

    private boolean isValidTransition(OperationState current, OperationState next) {
        return switch (current){
            case PENDING, RETRY_WAITING -> next==OperationState.PROCESSING;
            case PROCESSING -> next==OperationState.SUCCESS || next==OperationState.RETRY_WAITING;
            case SUCCESS -> false;
        };
    }

}
