package flowable.viability;

import java.util.ArrayList;

import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.common.engine.impl.EngineConfigurator;
import org.flowable.engine.configurator.ProcessEngineConfigurator;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.rules.RulesDeployer;

/**
 * Registra el RulesDeployer para que Flowable despliegue los archivos .drl.
 * Flowable lo carga solo desde META-INF/services, sin tocar la configuración de Flowable UI.
 */
public class RulesConfigurator implements EngineConfigurator {

    @Override
    public void beforeInit(AbstractEngineConfiguration engineConfiguration) {
        // Motor de procesos standalone (por ejemplo, en los tests)
        if (engineConfiguration instanceof ProcessEngineConfigurationImpl) {
            addRulesDeployer((ProcessEngineConfigurationImpl) engineConfiguration);
            return;
        }

        // Flowable UI: nos llama el motor principal (App Engine), que construye el de procesos más tarde
        for (EngineConfigurator configurator : engineConfiguration.getAllConfigurators()) {
            if (configurator instanceof ProcessEngineConfigurator) {
                addRulesDeployer((ProcessEngineConfigurationImpl)
                        ((ProcessEngineConfigurator) configurator).getProcessEngineConfiguration());
            }
        }
    }

    private void addRulesDeployer(ProcessEngineConfigurationImpl config) {
        if (config.getCustomPostDeployers() == null) {
            config.setCustomPostDeployers(new ArrayList<>());
        }
        config.getCustomPostDeployers().add(new RulesDeployer());
    }

    @Override
    public void configure(AbstractEngineConfiguration engineConfiguration) {
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
