package flowable.viability;

import java.io.Serializable;

public class AmountCursos implements Serializable {
    private static final long serialVersionUID = 1L;
    private long amountInstancesActives;

    public AmountCursos(long amountInstancesActives) {
        this.amountInstancesActives = amountInstancesActives;
    }

    public long getAmountInstancesActives() {
        return amountInstancesActives;
    }
}
