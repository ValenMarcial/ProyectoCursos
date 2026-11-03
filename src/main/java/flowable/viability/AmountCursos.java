package flowable.viability;

public class AmountCursos {
    private long amountInstancesActive;
    private boolean isViable;

    public AmountCursos(long amountInstancesActive) {
        this.amountInstancesActive = amountInstancesActive;
    }

    public long getAmountInstancesActive() {
        return amountInstancesActive;
    }

    public boolean isViable() {
        return isViable;
    }

    public void setIsViable(boolean isViable) {
        this.isViable = isViable;
    }
}
